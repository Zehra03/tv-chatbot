package com.paximum.paxassist.hotel.controller;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the hotel search HTTP contract matches the frontend ({@code frontend/src/api/hotelApi.ts}):
 * the frontend {@code HotelSearchCriteria} is accepted (checkOut→nights, adults→adult) and a bare
 * {@code HotelProduct[]} is returned (the internal status envelope is unwrapped).
 */
@ExtendWith(MockitoExtension.class)
class HotelControllerTest {

    @Mock
    private HotelSearchService hotelSearchService;

    @InjectMocks
    private HotelController hotelController;

    private MockMvc mockMvc;

    private static final String BODY = "{\"destination\":\"Antalya\",\"checkIn\":\"2026-08-01\","
            + "\"checkOut\":\"2026-08-05\",\"adults\":2,\"childAges\":[],\"nationality\":\"TR\","
            + "\"currency\":\"EUR\",\"rooms\":1,\"stars\":[5],\"sort\":\"price-asc\"}";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(hotelController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void search_returnsBareHotelArray() throws Exception {
        HotelProduct hotel = new HotelProduct("H1", "Rixos Premium", "Antalya", 5,
                new BigDecimal("1500.00"), "EUR", "All Inclusive", true);
        when(hotelSearchService.searchHotels(any())).thenReturn(HotelSearchResponse.success(List.of(hotel)));

        mockMvc.perform(post("/api/v1/hotels/search")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("H1"))
                .andExpect(jsonPath("$[0].hotelName").value("Rixos Premium"))
                .andExpect(jsonPath("$[0].region").value("Antalya"))
                .andExpect(jsonPath("$[0].stars").value(5))
                .andExpect(jsonPath("$[0].price").value(1500.00))
                .andExpect(jsonPath("$[0].boardType").value("All Inclusive"))
                .andExpect(jsonPath("$[0].availability").value(true));
    }

    @Test
    void mapsFrontendCriteriaToInternalRequest() throws Exception {
        ArgumentCaptor<HotelSearchRequest> captor = ArgumentCaptor.forClass(HotelSearchRequest.class);
        when(hotelSearchService.searchHotels(captor.capture()))
                .thenReturn(HotelSearchResponse.success(List.of()));

        mockMvc.perform(post("/api/v1/hotels/search")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        HotelSearchRequest internal = captor.getValue();
        assertThat(internal.destination()).isEqualTo("Antalya");
        assertThat(internal.checkIn()).isEqualTo("2026-08-01");
        assertThat(internal.night()).isEqualTo(4); // 2026-08-05 − 2026-08-01
        assertThat(internal.adult()).isEqualTo(2);
        assertThat(internal.currency()).isEqualTo("EUR");
    }
}
