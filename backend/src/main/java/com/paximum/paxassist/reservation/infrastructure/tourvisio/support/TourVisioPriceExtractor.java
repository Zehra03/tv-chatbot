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
     * @param payload the pricing-bearing node: {@code body.reservationData} on a transaction response,
     *                or the {@code body} itself on {@code getReservationDetail}, which nests the same
     *                {@code reservationInfo} block. Both shapes are accepted, so the caller does not
     *                have to know which endpoint it came from.
     * @return the amount the passenger is to pay, or empty when the payload carries none — meaning
     *         "could not verify", not "verified"
     */
    public static Optional<TourVisioPrice> extract(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return Optional.empty();
        }
        JsonNode reservationData = payload.get("reservationData");
        JsonNode source = (reservationData != null && reservationData.isObject()) ? reservationData : payload;
        JsonNode reservationInfo = source.get("reservationInfo");
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
        BigDecimal value = new BigDecimal(amount.asText());
        // A zero/negative amount is not a credible passenger price. After commit, getReservationDetail
        // reports the passenger-facing fields (notably priceToPay) as 0 — nothing is left to pay in the
        // no-real-payment booking flow — while the true sale price lives in a sibling field (salePrice /
        // totalPrice). Treating that 0 as "the price" made reconcileWithBookedPrice overwrite the verified
        // frozen amount with 0 and persist a €0 booking. Skip it so the caller falls through to the next
        // passenger field (or, if every field is 0, gets Optional.empty() = "could not verify").
        if (value.signum() <= 0) {
            return Optional.empty();
        }
        JsonNode currency = node.get("currency");
        return Optional.of(new TourVisioPrice(
                value,
                currency != null && currency.isTextual() ? currency.asText() : null));
    }
}
