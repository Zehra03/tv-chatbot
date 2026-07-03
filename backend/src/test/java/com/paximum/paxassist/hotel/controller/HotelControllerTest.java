package com.paximum.paxassist.hotel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotelController.class)
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HotelSearchService hotelSearchService;

    @Test
    void shouldReturnOkForValidHotelSearchRequest() throws Exception {
        // Given
        HotelSearchRequest request = new HotelSearchRequest(
                "Antalya", "2024-01-01", 5, 2, List.of(), "TR", "TRY", "tr-TR"
        );
        String requestBody = objectMapper.writeValueAsString(request);

        HotelSearchResponse mockResponse = HotelSearchResponse.success(List.of());
        when(hotelSearchService.searchHotels(any(HotelSearchRequest.class))).thenReturn(mockResponse);

        // When/Then
        mockMvc.perform(post("/api/v1/hotels/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"SUCCESS\",\"missingParameters\":[],\"results\":[]}"));
    }

    @Test
    void shouldReturnOkWithIncompleteStatusWhenDestinationIsMissing() throws Exception {
        // Given
        HotelSearchRequest request = new HotelSearchRequest(
                "", "2024-01-01", 5, 2, List.of(), "TR", "TRY", "tr-TR"
        );
        String requestBody = objectMapper.writeValueAsString(request);
        
        HotelSearchResponse mockResponse = HotelSearchResponse.incomplete(List.of("destination"));
        when(hotelSearchService.searchHotels(any(HotelSearchRequest.class))).thenReturn(mockResponse);

        // When/Then
        mockMvc.perform(post("/api/v1/hotels/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"INCOMPLETE\",\"missingParameters\":[\"destination\"],\"results\":null}"));
    }
}
