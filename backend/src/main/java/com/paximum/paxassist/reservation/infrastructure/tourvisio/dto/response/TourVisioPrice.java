package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A monetary amount as returned by TourVisio: {@code { amount, currency }}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioPrice(
        BigDecimal amount,
        String currency) {
}
