package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One service line inside a {@link CancelPenalty#services()}.
 *
 * <p>{@code productType} is TourVisio's numeric product discriminator (confirmed integer,
 * e.g. 2). {@code relatedServices} has no confirmed element schema yet (always empty in the
 * available samples), so it is intentionally typed as {@code List<Object>} — do NOT guess an
 * element structure; tighten it once a non-empty sample is available.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancellationService(
        String provider,
        String id,
        String code,
        Integer productType,
        String name,
        TourVisioPrice price,
        Boolean isCancelable,
        List<Object> relatedServices,
        CancellationPriceDetail priceDetail) {
}
