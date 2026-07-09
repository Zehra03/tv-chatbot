package com.paximum.paxassist.flight.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.paximum.paxassist.flight.domain.FlightLocation;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.service.FlightLocationService;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the flight search HTTP contract matches the frontend ({@code frontend/src/api/flightApi.ts}):
 * a bare {@code FlightProduct[]} carrying {@code tripType} and no internal {@code flightNumber}.
 */
@ExtendWith(MockitoExtension.class)
class FlightControllerTest {

    @Mock
    private FlightSearchService flightSearchService;

    @Mock
    private FlightLocationService flightLocationService;

    @InjectMocks
    private FlightController flightController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(flightController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void search_returnsBareFlightArrayWithTripType() throws Exception {
        FlightProduct product = FlightProduct.builder()
                .id("F1").airline("Turkish Airlines").flightNumber("TK1980")
                .origin("IST").destination("LHR")
                .departTime(Instant.parse("2026-08-10T09:00:00Z"))
                .arriveTime(Instant.parse("2026-08-10T13:00:00Z"))
                .stops(0).baggage("20kg").price(new BigDecimal("2500")).currency("EUR")
                .build();
        when(flightSearchService.search(any())).thenReturn(FlightSearchOutcome.complete(List.of(product)));

        mockMvc.perform(post("/api/v1/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"IST\",\"destination\":\"LHR\",\"departDate\":\"2026-08-10\","
                                + "\"passengers\":1,\"currency\":\"EUR\",\"tripType\":\"one_way\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("F1"))
                .andExpect(jsonPath("$[0].airline").value("Turkish Airlines"))
                .andExpect(jsonPath("$[0].tripType").value("one_way"))
                .andExpect(jsonPath("$[0].stops").value(0))
                .andExpect(jsonPath("$[0].baggage").value("20kg"))
                .andExpect(jsonPath("$[0].price").value(2500))
                .andExpect(jsonPath("$[0].flightNumber").doesNotExist());
    }

    @Test
    void search_incompleteReturnsEmptyArray() throws Exception {
        when(flightSearchService.search(any()))
                .thenReturn(FlightSearchOutcome.incomplete(List.of("origin")));

        mockMvc.perform(post("/api/v1/flights/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"LHR\",\"tripType\":\"one_way\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void locations_returnsSuggestionsForTheRequestedDirection() throws Exception {
        when(flightLocationService.suggest(eq("Ant"), eq(true)))
                .thenReturn(List.of(new FlightLocation("AYT", "AYT", "Antalya Havalimanı (AYT)", "airport")));

        mockMvc.perform(get("/api/v1/flights/locations").param("q", "Ant").param("direction", "departure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("AYT"))
                .andExpect(jsonPath("$[0].name").value("Antalya Havalimanı (AYT)"))
                .andExpect(jsonPath("$[0].type").value("airport"));
    }

    @Test
    void locations_shortQueryReturnsEmptyWithoutHittingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/flights/locations").param("q", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        org.mockito.Mockito.verifyNoInteractions(flightLocationService);
    }
}
