package com.paximum.paxassist.reservation.service;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.reservation.dto.CreateReservationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private final LogModuleClient logModuleClient;

    public ReservationService(LogModuleClient logModuleClient) {
        this.logModuleClient = logModuleClient;
    }

    public Map<String, Object> processReservation(CreateReservationRequest request, String userEmail) {
        log.info("Processing reservation for user: {}", userEmail);

        String maskedRequestData = buildMaskedLogData(request);

        try {
            // TODO: Asıl rezervasyon iş mantığı (Veritabanı kaydı, fiyat kontrolü vb.) burada yer alacak.
            if ("FAIL_SIMULATION".equals(request.getLeadGuestName())) {
                throw new RuntimeException("Simulated business logic failure");
            }

            Map<String, Object> responseData = Map.of(
                    "message", "Rezervasyon isteği geçerli ve işlendi.",
                    "user", userEmail,
                    "data", request
            );

            // Ana akışı bloklamadan (non-blocking) asenkron loglama tetikleniyor.
            logModuleClient.logActivity(
                    "ReservationModule",
                    "createReservation",
                    maskedRequestData,
                    "SUCCESS",
                    "Reservation successfully processed for user: " + userEmail
            );

            return responseData;
        } catch (Exception e) {
            log.error("Reservation processing failed for user: {}", userEmail, e);
            
            // Hata durumunun asenkron loglanması
            logModuleClient.logActivity(
                    "ReservationModule",
                    "createReservation",
                    maskedRequestData,
                    "FAILED",
                    "Error: " + e.getMessage()
            );
            
            throw e;
        }
    }

    private String buildMaskedLogData(CreateReservationRequest request) {
        if (request == null) return "null";
        
        StringBuilder sb = new StringBuilder();
        sb.append("CreateReservationRequest(productType=").append(request.getProductType());
        sb.append(", totalAmount=").append(request.getTotalAmount());
        sb.append(", currency=").append(request.getCurrency());
        sb.append(", leadGuestName=").append(maskString(request.getLeadGuestName()));
        
        sb.append(", passengers=[");
        if (request.getPassengers() != null) {
            for (int i = 0; i < request.getPassengers().size(); i++) {
                var p = request.getPassengers().get(i);
                sb.append("PassengerDto(firstName=").append(maskString(p.getFirstName()))
                  .append(", lastName=").append(maskString(p.getLastName()))
                  .append(", passengerType=").append(p.getPassengerType())
                  .append(", email=").append(maskEmail(p.getEmail()))
                  .append(", phone=").append(maskPhone(p.getPhone()))
                  .append(")");
                if (i < request.getPassengers().size() - 1) sb.append(", ");
            }
        }
        sb.append("]");
        
        if (request.getHotelDetails() != null) {
            sb.append(", hotelDetails=").append(request.getHotelDetails().toString());
        }
        if (request.getFlightDetails() != null) {
            sb.append(", flightDetails=").append(request.getFlightDetails().toString());
        }
        
        sb.append(")");
        return sb.toString();
    }

    private String maskString(String str) {
        if (str == null || str.isBlank()) return str;
        if (str.length() <= 2) return "***";
        return str.charAt(0) + "***" + str.charAt(str.length() - 1);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) {
            return "***@" + parts[1];
        }
        return parts[0].charAt(0) + "***" + parts[0].charAt(parts[0].length() - 1) + "@" + parts[1];
    }
    
    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) return "***";
        return phone.substring(0, 2) + "***" + phone.substring(phone.length() - 2);
    }
}
