package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code priceDetail} of a cancellable service: sale price, the penalty applied, and the main service fee. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancellationPriceDetail(
        BigDecimal totalSalePrice,
        BigDecimal penalty,
        BigDecimal mainServiceFee) {
}
