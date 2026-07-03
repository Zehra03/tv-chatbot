package com.paximum.paxassist.flight.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.dto.FlightSearchRequestDto;
import com.paximum.paxassist.flight.dto.FlightSearchResponseDto;
import com.paximum.paxassist.flight.mapper.FlightSearchMapper;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FlightControllerTest {

    @Mock
    private FlightSearchService flightSearchService;

    @Mock
    private FlightSearchMapper flightSearchMapper;

    @InjectMocks
    private FlightController flightController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(flightController)
                .setControllerAdvice(new FlightExceptionHandler())
                .build();
    }

    @Test
    void search_returnsOutcomeAndStatus200() throws Exception {
        FlightSearchRequestDto requestDto = new FlightSearchRequestDto(
                "IST", "LHR", null, null, null, null, "USD", false, "TK"
        );

        FlightSearchCriteria criteria = FlightSearchCriteria.builder().build();
        FlightSearchOutcome outcome = FlightSearchOutcome.complete(List.of());
        FlightSearchResponseDto responseDto = new FlightSearchResponseDto(
                com.paximum.paxassist.flight.dto.FlightSearchStatus.COMPLETE, 
                List.of(), 
                List.of()
        );

        when(flightSearchMapper.toDomain(any())).thenReturn(criteria);
        when(flightSearchService.search(criteria)).thenReturn(outcome);
        when(flightSearchMapper.toResponse(outcome)).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/flights/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETE"));

        verify(flightSearchService).search(criteria);
    }
}
