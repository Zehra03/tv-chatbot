package com.paximum.paxassist.flight.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.dto.FlightLocationDto;
import com.paximum.paxassist.flight.dto.FlightProductApiDto;
import com.paximum.paxassist.flight.dto.FlightSearchApiRequest;
import com.paximum.paxassist.flight.service.FlightLocationService;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;

/**
 * Frontend-facing flight search. Accepts the frontend's {@code FlightSearchCriteria} and returns a
 * bare {@code FlightProduct[]} (frontend contract, {@code frontend/src/api/flightApi.ts}). The
 * internal {@link FlightSearchService} keeps its outcome shape for the chat orchestrator; this
 * controller unwraps it and stamps each card with the trip type. The frontend validates criteria
 * first, so an incomplete search yields an empty list.
 */
@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {

    /** Below this length the origin/destination autocomplete is not worth a provider round-trip. */
    private static final int MIN_QUERY_LENGTH = 2;

    private final FlightSearchService flightSearchService;
    private final FlightLocationService flightLocationService;

    public FlightController(FlightSearchService flightSearchService,
                            FlightLocationService flightLocationService) {
        this.flightSearchService = flightSearchService;
        this.flightLocationService = flightLocationService;
    }

    @PostMapping("/search")
    public List<FlightProductApiDto> search(@RequestBody FlightSearchApiRequest request) {
        FlightSearchCriteria criteria = request.toCriteria();
        FlightSearchOutcome outcome = flightSearchService.search(criteria);
        return outcome.results().stream()
                .map(product -> FlightProductApiDto.from(product, criteria.getTripType()))
                .toList();
    }

    /**
     * Origin/destination autocomplete for the search form. {@code direction} selects the field:
     * {@code departure} (default) or {@code arrival}. Short queries return an empty list rather than
     * hitting TourVisio. The frontend sends the chosen {@code id} back as the search origin/destination.
     */
    @GetMapping("/locations")
    public List<FlightLocationDto> locations(
            @RequestParam("q") String query,
            @RequestParam(name = "direction", defaultValue = "departure") String direction) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        boolean departure = !"arrival".equalsIgnoreCase(direction);
        return flightLocationService.suggest(query, departure).stream()
                .map(FlightLocationDto::from)
                .toList();
    }
}
