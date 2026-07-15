package com.paximum.paxassist.reservation.infrastructure.tourvisio.support;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioPrice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the provider's own price out of a transaction response's {@code reservationData}.
 *
 * <p>The fixtures mirror the confirmed TourVisio payload: {@code reservationInfo} carries both a
 * passenger-facing total and the agency net, and the difference between them is the agency commission
 * (339 vs 306 EUR). Picking the wrong one would flag every honest booking as a price mismatch, so that
 * distinction is what most of these cases pin down.
 */
class TourVisioPriceExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Optional<TourVisioPrice> extract(String json) throws Exception {
        JsonNode node = json == null ? null : objectMapper.readTree(json);
        return TourVisioPriceExtractor.extract(node);
    }

    /** The real shape: passenger fields and agency fields side by side under reservationInfo. */
    private static final String FULL_PRICING = """
            {
              "travellers": [
                {"id": "1", "services": [{"id": "1", "type": 2, "price": {"amount": 169.5, "currency": "EUR"}}]}
              ],
              "services": [{"id": "1", "productType": 2, "price": {"amount": 339, "currency": "EUR"}}],
              "reservationInfo": {
                "salePrice": {"amount": 339, "currency": "EUR"},
                "passengerAmountToPay": {"amount": 339, "currency": "EUR"},
                "agencyAmountToPay": {"amount": 306, "currency": "EUR"},
                "priceToPay": {"amount": 339, "currency": "EUR"},
                "agencyPriceToPay": {"amount": 306, "currency": "EUR"},
                "passengerPriceToPay": {"amount": 339, "currency": "EUR"},
                "totalPrice": {"amount": 339, "currency": "EUR"},
                "agencyCommission": {"percent": 10, "amount": 34, "currency": "EUR"}
              }
            }
            """;

    @Test
    void readsThePassengerPriceToPay() throws Exception {
        Optional<TourVisioPrice> price = extract(FULL_PRICING);

        assertThat(price).isPresent();
        assertThat(price.get().amount()).isEqualByComparingTo(new BigDecimal("339"));
        assertThat(price.get().currency()).isEqualTo("EUR");
    }

    @Test
    void neverReadsTheAgencyNetOrTheCommission() throws Exception {
        // 306 is what the agency pays and 34 is the commission — neither is what the user agreed to.
        BigDecimal amount = extract(FULL_PRICING).get().amount();

        assertThat(amount).isNotEqualByComparingTo(new BigDecimal("306"));
        assertThat(amount).isNotEqualByComparingTo(new BigDecimal("34"));
    }

    @Test
    void neverReadsAPerServiceOrPerTravellerLineItem() throws Exception {
        // 169.5 is one traveller's share and 339 happens to be the service total; the amount must come
        // from reservationInfo, not from a line item that could coincidentally look right.
        assertThat(extract("""
                {
                  "travellers": [{"services": [{"price": {"amount": 169.5, "currency": "EUR"}}]}],
                  "services": [{"price": {"amount": 339, "currency": "EUR"}}]
                }
                """)).isEmpty();
    }

    @Test
    void fallsBackToTheOtherPassengerFieldsWhenPriceToPayIsAbsent() throws Exception {
        assertThat(extract("""
                {"reservationInfo": {"totalPrice": {"amount": 1500.50, "currency": "TRY"}}}
                """).get().amount()).isEqualByComparingTo(new BigDecimal("1500.50"));

        assertThat(extract("""
                {"reservationInfo": {"passengerAmountToPay": {"amount": 750, "currency": "EUR"}}}
                """).get().amount()).isEqualByComparingTo(new BigDecimal("750"));
    }

    @Test
    void aPayloadWithNoPricing_isUnverifiableNotZero() throws Exception {
        // The whole point: absence must be reported as absence. A 0 or a false "match" here would
        // silently wave a tampered amount through.
        assertThat(extract("""
                {"reservationInfo": {"status": 1}, "travellers": []}
                """)).isEmpty();
        assertThat(extract("{}")).isEmpty();
        assertThat(extract("null")).isEmpty();
        assertThat(extract(null)).isEmpty();
    }

    @Test
    void aPricingBlockWithoutANumericAmount_isNotAPrice() throws Exception {
        assertThat(extract("""
                {"reservationInfo": {"priceToPay": {"currency": "EUR"}}}
                """)).isEmpty();
    }

    @Test
    void aZeroPriceToPay_fallsThroughToTheRealSalePrice() throws Exception {
        // Post-commit getReservationDetail: priceToPay is 0 (nothing left to pay in the no-real-payment
        // flow), but the sale price is still present. The extractor must read the real price, not the 0,
        // so reconcileWithBookedPrice does not persist a €0 booking.
        Optional<TourVisioPrice> price = extract("""
                {"reservationInfo": {
                   "priceToPay": {"amount": 0, "currency": "EUR"},
                   "salePrice": {"amount": 20, "currency": "EUR"}
                }}
                """);
        assertThat(price).isPresent();
        assertThat(price.get().amount()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void aReservationInfoWhereEveryPassengerFieldIsZero_isUnverifiableNotZero() throws Exception {
        // If TourVisio echoes every passenger amount as 0, that is "could not verify", never a real €0
        // price — the caller keeps the already-verified frozen amount instead of overwriting it with 0.
        assertThat(extract("""
                {"reservationInfo": {
                   "priceToPay": {"amount": 0, "currency": "EUR"},
                   "passengerPriceToPay": {"amount": 0, "currency": "EUR"},
                   "passengerAmountToPay": {"amount": 0, "currency": "EUR"},
                   "totalPrice": {"amount": 0, "currency": "EUR"},
                   "salePrice": {"amount": 0, "currency": "EUR"}
                }}
                """)).isEmpty();
    }
}
