package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CommitTransaction — the point-of-no-return purchase call. CONFIRMED payload:
 * {@code { "transactionId": "...", "PaymentInformation": { ... } }}.
 *
 * <p><b>No real payment ever happens</b> — {@link PaymentInformation} is a fixed dummy placeholder
 * block (SanTSG test account). Note the mixed casing that TourVisio actually expects here:
 * {@code transactionId} is camelCase while the {@code PaymentInformation} block and its fields are
 * PascalCase.
 */
public record CommitTransactionRequest(
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("PaymentInformation") PaymentInformation paymentInformation) {

    /** Fixed dummy payment block — no funds are captured. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentInformation(
            @JsonProperty("AccountName") String accountName,
            @JsonProperty("PaymentTypeId") Integer paymentTypeId,
            @JsonProperty("PaymentPrice") PaymentPrice paymentPrice,
            @JsonProperty("InstallmentCount") String installmentCount,
            @JsonProperty("PaymentDate") String paymentDate,
            @JsonProperty("ReceiptType") String receiptType,
            @JsonProperty("Reference") String reference,
            @JsonProperty("PaymentToken") String paymentToken) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentPrice(
            @JsonProperty("Amount") BigDecimal amount,
            @JsonProperty("Currency") String currency) {
    }
}
