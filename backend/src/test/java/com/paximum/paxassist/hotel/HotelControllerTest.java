package com.paximum.paxassist.hotel;

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

    @MockBean
    private HotelSearchService hotelSearchService;

    @Test
    void shouldReturnOkForValidHotelSearchRequest() throws Exception {
        // Given
        String requestBody = "{\"destination\":\"Antalya\"}";
        List<HotelProduct> mockedResponse = List.of(new HotelProduct("1", "Rixos Premium", "Antalya"));
        when(hotelSearchService.searchHotels(any(HotelSearchCriteria.class))).thenReturn(mockedResponse);

        // When/Then
        mockMvc.perform(post("/api/v1/hotels/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":\"1\",\"hotelName\":\"Rixos Premium\",\"destination\":\"Antalya\"}]"));
    }

    @Test
    void shouldReturnBadRequestWhenDestinationIsMissing() throws Exception {
        // Given
        String requestBody = "{\"destination\":\"\"}"; // Validation should catch empty strings

        // When/Then
        mockMvc.perform(post("/api/v1/hotels/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
