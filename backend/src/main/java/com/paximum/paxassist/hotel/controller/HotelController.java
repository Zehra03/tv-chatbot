package com.paximum.paxassist.hotel.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchApiRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;

/**
 * Frontend-facing hotel search. Accepts the frontend's {@code HotelSearchCriteria} and returns a
 * bare {@code HotelProduct[]} (frontend contract, {@code frontend/src/api/hotelApi.ts}). The
 * internal {@link HotelSearchService} keeps its status-envelope shape for the chat orchestrator;
 * this controller unwraps it. The frontend validates criteria before calling, so an incomplete
 * search simply yields an empty list.
 */
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    private final HotelSearchService hotelSearchService;

    public HotelController(HotelSearchService hotelSearchService) {
        this.hotelSearchService = hotelSearchService;
    }

    @PostMapping("/search")
    public List<HotelProduct> search(@RequestBody HotelSearchApiRequest request) {
        HotelSearchResponse response = hotelSearchService.searchHotels(request.toInternal());
        return toProducts(response.results());
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
