package com.paximum.paxassist.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.dto.CreateReservationRequest;
import com.paximum.paxassist.reservation.dto.PassengerDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class ReservationLoggingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Gerçek loglama HTTP isteği atılmasını önlemek ve metot çağrısını yakalamak için mock kullanıyoruz.
    @MockBean
    private LogModuleClient logModuleClient;

    @Test
    @WithMockUser(username = "testuser@example.com")
    void shouldProcessReservationAndTriggerAsyncLog() throws Exception {
        // 1. Geçerli bir rezervasyon isteği oluştur (CreateReservationRequest)
        CreateReservationRequest request = new CreateReservationRequest();
        request.setProductType(ProductType.HOTEL);
        request.setTotalAmount(new BigDecimal("1500.00"));
        request.setCurrency("TRY");
        request.setLeadGuestName("Ahmet Yilmaz");

        PassengerDto passenger = new PassengerDto();
        passenger.setFirstName("Ahmet");
        passenger.setLastName("Yilmaz");
        passenger.setPassengerType(PassengerType.ADULT);
        request.setPassengers(List.of(passenger));

        String requestJson = objectMapper.writeValueAsString(request);

        // 2. HTTP POST isteğini gönder
        // MockMvc asenkron loglamayı beklemeden hemen 201 döndürmeli
        MvcResult result = mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Rezervasyon isteği geçerli ve işlendi."))
                .andExpect(jsonPath("$.user").value("testuser@example.com"))
                .andReturn();

        // 3. Loglama asenkron tetiklendiği için kısa bir timeout ile çağrının yapıldığını doğrula
        verify(logModuleClient, timeout(1000).times(1)).logActivity(
                eq("ReservationModule"),
                eq("createReservation"),
                anyString(), // request toString result
                eq("SUCCESS"),
                anyString()
        );
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void shouldLogFailedStatusWhenExceptionOccurs() throws Exception {
        // 1. Hataya sebep olacak rezervasyon isteği oluştur (isim "FAIL_SIMULATION" verildiğinde hata atması için kodlandı)
        CreateReservationRequest request = new CreateReservationRequest();
        request.setProductType(ProductType.HOTEL);
        request.setTotalAmount(new BigDecimal("1500.00"));
        request.setCurrency("TRY");
        request.setLeadGuestName("FAIL_SIMULATION");

        PassengerDto passenger = new PassengerDto();
        passenger.setFirstName("Fail");
        passenger.setLastName("Simulation");
        passenger.setPassengerType(PassengerType.ADULT);
        request.setPassengers(List.of(passenger));

        String requestJson = objectMapper.writeValueAsString(request);

        // 2. HTTP POST isteğini gönder
        // İş mantığı exception fırlatacağı için 500 Internal Server Error dönecektir (ya da global exception handler ne ayarladıysa)
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());

        // 3. FAILED logunun asenkron atıldığını doğrula
        verify(logModuleClient, timeout(1000).times(1)).logActivity(
                eq("ReservationModule"),
                eq("createReservation"),
                anyString(),
                eq("FAILED"),
                anyString()
        );
    }
}
