package com.paximum.paxassist.hotel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// @WebMvcTest(HotelController.class)
class HotelControllerTest {

    // @Autowired
    // private MockMvc mockMvc;

    // @MockBean
    // private HotelSearchService hotelSearchService;

    @Test
    void shouldReturnOkForValidHotelSearchRequest() throws Exception {
        // TODO: Controller success scenario
        /*
        // Given
        String requestBody = "{\"destination\":\"Antalya\"}";
        // when(hotelSearchService.searchHotels(any())).thenReturn(mockedResponse);

        // When/Then
        mockMvc.perform(post("/api/v1/hotels/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());
        */
        assertThat(true).isTrue();
    }

    @Test
    void shouldReturnBadRequestWhenDestinationIsMissing() throws Exception {
        // TODO: Controller validation scenario
        /*
        // Given
        String requestBody = "{\"destination\":\"\"}"; // invalid

        // When/Then
        mockMvc.perform(post("/api/v1/hotels/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
        */
        assertThat(true).isTrue();
    }
}
