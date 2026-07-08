package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

/**
 * Common contract for every TourVisio response: they all carry the shared
 * {@link TourVisioResponseHeader} envelope ({@code requestId}, {@code success},
 * {@code messages[]}). Letting the booking client read the header generically is
 * what allows one mapping path to classify success vs. business failure for all
 * endpoints.
 */
public interface TourVisioResponse {

    TourVisioResponseHeader header();
}
