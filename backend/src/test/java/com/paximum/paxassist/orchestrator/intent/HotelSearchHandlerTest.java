package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationCatalog;
import com.paximum.paxassist.orchestrator.slot.SlotGuard;
import com.paximum.paxassist.orchestrator.mapper.HotelCriteriaMapper;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelSearchHandlerTest {

    @Mock
    private SlotFillingService slotFilling;
    @Mock
    private HotelSearchService hotelSearchService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SlotGuard slotGuard;

    private HotelSearchHandler handler() {
        slotGuard = mock(SlotGuard.class);
        when(slotGuard.checkInvalidSlots(any())).thenReturn(Optional.empty());
        return new HotelSearchHandler(
                slotFilling, new HotelCriteriaMapper(), hotelSearchService, new ClarificationCatalog(), slotGuard);
    }

    private OrchestrationContext contextWith(SlotCriteria merged) {
        when(slotFilling.accumulate(any(), any())).thenReturn(merged);
        return new OrchestrationContext(new ChatSession("s1"), "otel", IntentType.HOTEL, merged);
    }

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    @Test
    void incompleteSearchBecomesClarifyingQuestion() {
        OrchestrationContext context = contextWith(slots(Map.of()));
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.incomplete(List.of("destination")));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.reply()).contains("şehir");
    }

    @Test
    void invalidLocationReturnsFriendlyClarify() {
        OrchestrationContext context = contextWith(slots(Map.of("location", "lulubumbu")));
        context.session().getAccumulatedCriteria().put("location", "lulubumbu");
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.invalidLocation("lulubumbu"));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.reply()).contains("lulubumbu").contains("bulunamadı");
        assertThat(context.session().getAccumulatedCriteria()).doesNotContainKey("location");
    }

    @Test
    void successReturnsCardsAndUpdatesSession() {
        SlotCriteria merged = slots(
                Map.of("location", "Antalya", "checkIn", "2026-08-01", "checkOut", "2026-08-05", "adults", 2));
        OrchestrationContext context = contextWith(merged);
        HotelProduct h1 = new HotelProduct("H1", "Rixos", "Antalya", 5, new BigDecimal("1500"), "EUR", "AI", true);
        HotelProduct h2 = new HotelProduct("H2", "Titanic", "Antalya", 5, new BigDecimal("2000"), "EUR", "AI", true);
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.success(List.of(h1, h2)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(h1, h2);
        assertThat(context.session().getActiveDomain()).isEqualTo("HOTEL");
        assertThat(context.session().getLastResultCards()).containsExactly(h1, h2);
    }

    @Test
    void pastDateGuard_shortCircuitsBeforeSearch() {
        HotelSearchHandler handler = handler();
        when(slotGuard.checkInvalidSlots(any()))
                .thenReturn(Optional.of("Girdiğiniz tarih geçmişte kalıyor"));

        OrchestrationContext context = contextWith(slots(Map.of("checkIn", "2026-06-01")));

        OrchestrationResult result = handler.handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("geçmiş");
        org.mockito.Mockito.verifyNoInteractions(hotelSearchService);
    }

    @Test
    void maxPriceFiltersOutTooExpensiveCards() {
        SlotCriteria merged = slots(Map.of(
                "location", "Antalya", "checkIn", "2026-08-01", "checkOut", "2026-08-05",
                "adults", 2, "hotelMaxPrice", 1800));
        OrchestrationContext context = contextWith(merged);
        HotelProduct cheap = new HotelProduct("H1", "Blue", "Antalya", 4, new BigDecimal("1500"), "TRY", "HB", true);
        HotelProduct pricey = new HotelProduct("H2", "Rixos", "Antalya", 5, new BigDecimal("2000"), "TRY", "AI", true);
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.success(List.of(cheap, pricey)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(cheap);
    }

    @Test
    void maxPriceRemovingAllCardsGivesHonestBudgetMessage() {
        SlotCriteria merged = slots(Map.of(
                "location", "Antalya", "checkIn", "2026-08-01", "checkOut", "2026-08-05",
                "adults", 2, "hotelMaxPrice", 10, "currency", "TRY"));
        OrchestrationContext context = contextWith(merged);
        HotelProduct pricey = new HotelProduct("H2", "Rixos", "Antalya", 5, new BigDecimal("2000"), "TRY", "AI", true);
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.success(List.of(pricey)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("10").contains("altında");
    }

    @Test
    void seafrontFeatureKeepsOnlyBeachHotels() {
        SlotCriteria merged = slots(Map.of(
                "location", "Antalya", "checkIn", "2026-08-01", "checkOut", "2026-08-05",
                "adults", 2, "features", List.of("SEAFRONT")));
        OrchestrationContext context = contextWith(merged);
        HotelProduct beach = new HotelProduct("H1", "Rixos", "Antalya", 5, new BigDecimal("1500"), "TRY", "AI", true,
                null, List.of("Beach Hotel", "Private Beach"));
        HotelProduct city = new HotelProduct("H2", "City Inn", "Antalya", 4, new BigDecimal("1200"), "TRY", "BB", true,
                null, List.of("City Hotel", "Business Center"));
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.success(List.of(beach, city)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(beach);
        assertThat(result.reply()).contains("denize sıfır");
    }

    @Test
    void seafrontWithNoMatchGivesHonestFeatureMessage() {
        SlotCriteria merged = slots(Map.of(
                "location", "Ankara", "checkIn", "2026-08-01", "checkOut", "2026-08-05",
                "adults", 2, "features", List.of("SEAFRONT")));
        OrchestrationContext context = contextWith(merged);
        HotelProduct city1 = new HotelProduct("H1", "Ankara Palace", "Ankara", 5, new BigDecimal("1500"), "TRY", "BB", true,
                null, List.of("City Hotel"));
        HotelProduct city2 = new HotelProduct("H2", "Kızılay Inn", "Ankara", 4, new BigDecimal("1200"), "TRY", "BB", true,
                null, List.of("Business Center"));
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.success(List.of(city1, city2)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        // Honest: blame the unmet feature, not the date/city.
        assertThat(result.reply()).contains("denize sıfır").doesNotContain("Farklı bir tarih veya şehir");
    }

    @Test
    void boardTypeFiltersHotelsByBoardName() {
        SlotCriteria merged = slots(Map.of(
                "location", "Antalya", "checkIn", "2026-08-01", "checkOut", "2026-08-05",
                "adults", 2, "boardType", "HB"));
        OrchestrationContext context = contextWith(merged);
        HotelProduct halfBoard = new HotelProduct("H1", "Blue", "Antalya", 4, new BigDecimal("1500"), "TRY", "Yarım Pansiyon", true);
        HotelProduct allInclusive = new HotelProduct("H2", "Rixos", "Antalya", 5, new BigDecimal("2000"), "TRY", "Herşey Dahil", true);
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.success(List.of(halfBoard, allInclusive)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(halfBoard);
    }
}
