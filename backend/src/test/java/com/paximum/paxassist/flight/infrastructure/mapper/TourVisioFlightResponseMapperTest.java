package com.paximum.paxassist.flight.infrastructure.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAirline;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAirport;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightItem;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightPoint;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightResult;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioOffer;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPrice;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseBody;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseHeader;

import static org.assertj.core.api.Assertions.assertThat;

class TourVisioFlightResponseMapperTest {

    private static final TourVisioProperties VALID_PROPERTIES =
            new TourVisioProperties("https://test.example.com", "en-US", "Europe/Istanbul", "a", "u", "p");

    private TourVisioFlightItem validItem() {
        return new TourVisioFlightItem(
                "TK123",
                120,
                0,
                new TourVisioAirline("TK", "Turkish Airlines"),
                new TourVisioFlightPoint(new TourVisioAirport("IST"), "2026-08-01T10:00:00"),
                new TourVisioFlightPoint(new TourVisioAirport("LHR"), "2026-08-01T12:00:00"),
                List.of());
    }

    private TourVisioFlightResult validFlight(TourVisioFlightItem item) {
        return new TourVisioFlightResult(
                "flight-1",
                List.of(item),
                new TourVisioOffer("offer-1", new TourVisioPrice(BigDecimal.TEN, "USD")));
    }

    private TourVisioPriceSearchResponse responseWith(TourVisioFlightResult... flights) {
        return new TourVisioPriceSearchResponse(
                new TourVisioResponseHeader(true),
                new TourVisioResponseBody("search-1", List.of(flights)));
    }

    @Test
    void toFlightProducts_skipsFlightWithNullDepartureAirport() {
        TourVisioFlightItem item = new TourVisioFlightItem(
                "TK123", 120, 0,
                new TourVisioAirline("TK", "Turkish Airlines"),
                new TourVisioFlightPoint(null, "2026-08-01T10:00:00"),
                new TourVisioFlightPoint(new TourVisioAirport("LHR"), "2026-08-01T12:00:00"),
                List.of());
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(responseWith(validFlight(item)), TripType.ONE_WAY);

        assertThat(products).isEmpty();
    }

    @Test
    void toFlightProducts_skipsFlightWithNullArrivalAirport() {
        TourVisioFlightItem item = new TourVisioFlightItem(
                "TK123", 120, 0,
                new TourVisioAirline("TK", "Turkish Airlines"),
                new TourVisioFlightPoint(new TourVisioAirport("IST"), "2026-08-01T10:00:00"),
                new TourVisioFlightPoint(null, "2026-08-01T12:00:00"),
                List.of());
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(responseWith(validFlight(item)), TripType.ONE_WAY);

        assertThat(products).isEmpty();
    }

    @Test
    void toFlightProducts_skipsFlightWhenTimezoneMissing() {
        TourVisioProperties blankTimezoneProperties =
                new TourVisioProperties("https://test.example.com", "en-US", "  ", "a", "u", "p");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(blankTimezoneProperties);

        List<FlightProduct> products =
                mapper.toFlightProducts(responseWith(validFlight(validItem())), TripType.ONE_WAY);

        assertThat(products).isEmpty();
    }

    @Test
    void toFlightProducts_mapsValidFlightWithCorrectInstant() {
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products =
                mapper.toFlightProducts(responseWith(validFlight(validItem())), TripType.ONE_WAY);

        assertThat(products).hasSize(1);
        FlightProduct product = products.get(0);
        assertThat(product.getOrigin()).isEqualTo("IST");
        assertThat(product.getDestination()).isEqualTo("LHR");
        assertThat(product.getAirline()).isEqualTo("TK");
        assertThat(product.getDepartTime()).isEqualTo(
                Instant.parse("2026-08-01T07:00:00Z"));
    }
}
