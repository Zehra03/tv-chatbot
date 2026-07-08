package com.paximum.paxassist.flight.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.dto.FlightProductApiDto;
import com.paximum.paxassist.flight.dto.FlightSearchApiRequest;
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

    private final FlightSearchService flightSearchService;

    public FlightController(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    @PostMapping("/search")
    public List<FlightProductApiDto> search(@RequestBody FlightSearchApiRequest request) {
        FlightSearchCriteria criteria = request.toCriteria();
        FlightSearchOutcome outcome = flightSearchService.search(criteria);
        return outcome.results().stream()
                .map(product -> FlightProductApiDto.from(product, criteria.getTripType()))
                .toList();
    }
}
