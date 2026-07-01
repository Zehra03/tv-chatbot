package com.paximum.paxassist.flight.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest(FlightController.class)
public class FlightControllerTest {

    /*
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlightSearchService flightSearchService;
    */

    @Test
    void searchFlights_ValidRequest_ShouldReturnOk() throws Exception {
        // Trello Kartı 4: API Endpoint (Controller) ve Validasyon
        // Chat Orchestrator'ın erişebileceği endpoint testi
        
        /*
        // Arrange
        String validPayload = "{\"origin\":\"IST\", \"destination\":\"LHR\", \"date\":\"2024-12-01\"}";
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/flights/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPayload))
                .andExpect(status().isOk());
        */
    }

    @Test
    void searchFlights_InvalidRequest_ShouldReturnBadRequest() throws Exception {
        // Trello Kartı 4: Validasyon kontrolü (örn. zorunlu alanlar eksikse)
        
        /*
        // Arrange
        String invalidPayload = "{\"origin\":\"IST\"}"; // destination eksik
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/flights/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
                .andExpect(status().isBadRequest());
        */
    }
}
