package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code priceDetail} of a cancellable service: sale price, the penalty applied, and the main service fee.
 *
 * <p>Each field is a TourVisio {@code { amount, currency }} object ({@link TourVisioPrice}), NOT a bare
 * number — same shape as {@link CancellationService#price()}. Modelling these as {@code BigDecimal}
 * caused a Jackson {@code MismatchedInputException} (START_OBJECT → BigDecimal) that 500'd the whole
 * reservation-detail response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancellationPriceDetail(
        TourVisioPrice totalSalePrice,
        TourVisioPrice penalty,
        TourVisioPrice mainServiceFee) {
}
