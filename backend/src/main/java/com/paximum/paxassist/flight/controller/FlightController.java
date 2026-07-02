package com.paximum.paxassist.flight.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.dto.FlightSearchRequestDto;
import com.paximum.paxassist.flight.dto.FlightSearchResponseDto;
import com.paximum.paxassist.flight.mapper.FlightSearchMapper;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;

@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {

    private final FlightSearchService flightSearchService;
    private final FlightSearchMapper flightSearchMapper;

    public FlightController(FlightSearchService flightSearchService, FlightSearchMapper flightSearchMapper) {
        this.flightSearchService = flightSearchService;
        this.flightSearchMapper = flightSearchMapper;
    }

    @PostMapping("/search")
    public FlightSearchResponseDto search(@Valid @RequestBody FlightSearchRequestDto request) {
        FlightSearchCriteria criteria = flightSearchMapper.toDomain(request);
        FlightSearchOutcome outcome = flightSearchService.search(criteria);
        return flightSearchMapper.toResponse(outcome);
    }
}
