package com.paximum.paxassist.hotel.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.hotel.HotelDetailsService;
import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelFeatureDetails;
import com.paximum.paxassist.hotel.dto.HotelLocationDto;
import com.paximum.paxassist.hotel.dto.HotelSearchApiRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;

import jakarta.validation.Valid;

/**
 * Frontend-facing hotel search. Accepts the frontend's {@code HotelSearchCriteria} and returns a
 * bare {@code HotelProduct[]} (frontend contract, {@code frontend/src/api/hotelApi.ts}). The
 * internal {@link HotelSearchService} keeps its status-envelope shape for the chat orchestrator;
 * this controller unwraps it. Criteria are validated at this boundary (see
 * {@link HotelSearchApiRequest}) — an invalid body is rejected with 400 before any provider call;
 * a valid but resultless search yields an empty list.
 */
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    /** Below this length the destination autocomplete is not worth a provider round-trip. */
    private static final int MIN_QUERY_LENGTH = 2;

    private final HotelSearchService hotelSearchService;
    private final HotelDetailsService hotelDetailsService;

    public HotelController(HotelSearchService hotelSearchService, HotelDetailsService hotelDetailsService) {
        this.hotelSearchService = hotelSearchService;
        this.hotelDetailsService = hotelDetailsService;
    }

    @PostMapping("/search")
    public List<HotelProduct> search(@Valid @RequestBody HotelSearchApiRequest request) {
        HotelSearchResponse response = hotelSearchService.searchHotels(request.toInternal());
        return toProducts(response.results());
    }

    /**
     * Detail-screen hotel feature model (pet-friendliness, grouped facilities, normalized board
     * options, theme filters) for a single hotel. Uses one {@code GetProductInfo} call — never
     * invoked per-result on the listing screen. See {@code HotelFeatureDetails}.
     *
     * <p>{@code ownerProvider} is the {@code provider} the search card carried
     * ({@code HotelProduct#provider()}); TourVisio GetProductInfo requires it, so the frontend passes
     * it straight back from the selected result. {@code boardType} is likewise the board the card
     * already showed ({@code HotelProduct#boardType()}) — passed back so the detail model can list it
     * without an extra provider call (GetProductInfo has no board data). It is optional.
     */
    @GetMapping("/{id}/details")
    public HotelFeatureDetails details(@PathVariable("id") String id,
                                       @RequestParam("ownerProvider") Integer ownerProvider,
                                       @RequestParam(value = "boardType", required = false) String boardType) {
        return hotelDetailsService.getFeatureDetails(id, ownerProvider, boardType);
    }

    /**
     * Destination autocomplete for the search form. Short queries return an empty list rather than
     * hitting TourVisio. The frontend sends the chosen place name back as the search destination.
     */
    @GetMapping("/locations")
    public List<HotelLocationDto> locations(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return hotelSearchService.suggestLocations(query);
    }

    /** Unwrap the envelope's untyped results into typed hotel cards; anything else → empty. */
    private static List<HotelProduct> toProducts(Object results) {
        if (results instanceof List<?> list) {
            return list.stream()
                    .filter(HotelProduct.class::isInstance)
                    .map(HotelProduct.class::cast)
                    .toList();
        }
        return List.of();
    }
}
