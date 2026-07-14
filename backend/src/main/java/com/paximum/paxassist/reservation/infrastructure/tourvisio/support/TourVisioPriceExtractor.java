package com.paximum.paxassist.reservation.infrastructure.tourvisio.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioPrice;

/**
 * Reads the price TourVisio itself put on a transaction out of the {@code reservationData} node of a
 * {@code TransactionResponse} (BeginTransaction / SetReservationInfo).
 *
 * <p>{@code commitTransaction} echoes identifiers only ({@code reservationNumber},
 * {@code encryptedReservationNumber}, {@code transactionId}) and carries no pricing, so the transaction
 * responses are where the provider's own amount can be seen before the purchase.
 *
 * <p><b>Confirmed schema</b> — {@code reservationData.reservationInfo} carries a set of money objects
 * ({@code { amount, currency }}):
 * <pre>
 *   salePrice, priceToPay, passengerPriceToPay, passengerAmountToPay, totalPrice   → what the PASSENGER pays
 *   agencyPriceToPay, agencyAmountToPay, agencyCommission                          → the AGENCY's net
 * </pre>
 *
 * <p>Only the passenger-facing fields are read. {@code totalAmount} in our command is the amount shown
 * to and accepted by the user, so comparing it against the agency net (which is lower — it is the price
 * minus commission) would flag every correct booking as a mismatch.
 *
 * <p>{@code reservationData} is still an untyped {@link JsonNode} on the response record, so a missing
 * or renamed field yields {@link Optional#empty()} — which the caller must treat as "could not verify",
 * never as "verified".
 */
public final class TourVisioPriceExtractor {

    /**
     * Passenger-facing amount fields, most authoritative first. {@code priceToPay} is what the booking
     * will actually be charged; the rest are equivalent in the confirmed payloads and act as fallbacks
     * if a given operation omits it.
     */
    private static final List<String> PASSENGER_AMOUNT_FIELDS = List.of(
            "priceToPay", "passengerPriceToPay", "passengerAmountToPay", "totalPrice", "salePrice");

    private TourVisioPriceExtractor() {
    }

    /**
     * @param reservationData the {@code body.reservationData} node of a transaction response
     * @return the amount the passenger is to pay, or empty when the payload carries none — meaning
     *         "could not verify", not "verified"
     */
    public static Optional<TourVisioPrice> extract(JsonNode reservationData) {
        if (reservationData == null || !reservationData.isObject()) {
            return Optional.empty();
        }
        JsonNode reservationInfo = reservationData.get("reservationInfo");
        if (reservationInfo == null || !reservationInfo.isObject()) {
            return Optional.empty();
        }
        for (String field : PASSENGER_AMOUNT_FIELDS) {
            Optional<TourVisioPrice> price = asPrice(reservationInfo.get(field));
            if (price.isPresent()) {
                return price;
            }
        }
        return Optional.empty();
    }

    /** A money object is {@code { amount, currency }}; anything without a numeric amount is not money. */
    private static Optional<TourVisioPrice> asPrice(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        JsonNode amount = node.get("amount");
        if (amount == null || !amount.isNumber()) {
            return Optional.empty();
        }
        JsonNode currency = node.get("currency");
        return Optional.of(new TourVisioPrice(
                new BigDecimal(amount.asText()),
                currency != null && currency.isTextual() ? currency.asText() : null));
    }
}
