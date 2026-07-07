package com.paximum.paxassist.reservation.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
import com.paximum.paxassist.reservation.domain.TripType;

/**
 * Response of GET /api/v1/reservations/{id}: the full reservation plus the live TourVisio
 * {@link #cancellationOptions()} (reasons + per-service penalty pricing) so the frontend can show
 * cancellation cost on the same screen. {@code cancellationOptions} is empty when the reservation has
 * no external reference or the penalty lookup failed.
 */
public record ReservationDetailResponse(
        Long id,
        String reservationNumber,
        String externalReservationNumber,
        ReservationStatus status,
        ProductType productType,
        LocalDate reservationDate,
        BigDecimal totalAmount,
        String currency,
        String leadGuestName,
        List<Passenger> passengers,
        Hotel hotel,
        Flight flight,
        List<CancellationOption> cancellationOptions) {

    public record Passenger(
            String firstName,
            String lastName,
            PassengerType passengerType,
            Integer age,
            String nationality,
            String email,
            String phone) {
    }

    public record Hotel(
            String hotelName,
            String region,
            Short stars,
            String boardType,
            LocalDate checkIn,
            LocalDate checkOut,
            Short rooms,
            Short adults,
            Short children,
            String nationality,
            BigDecimal price,
            String currency) {
    }

    public record Flight(
            String origin,
            String destination,
            String airline,
            TripType tripType,
            OffsetDateTime departTime,
            OffsetDateTime arriveTime,
            OffsetDateTime returnDepartTime,
            OffsetDateTime returnArriveTime,
            Short stops,
            String baggage,
            Short passengerCount,
            BigDecimal price,
            String currency) {
    }

    /** One cancellation reason the user can pick, with its overall price and per-service breakdown. */
    public record CancellationOption(
            String reasonId,
            String reasonName,
            String reasonComment,
            Boolean cancelable,
            Money price,
            List<CancellationServiceOption> services) {
    }

    public record CancellationServiceOption(
            String id,
            String code,
            String name,
            Integer productType,
            Boolean cancelable,
            Money price,
            PriceDetail priceDetail) {
    }

    public record PriceDetail(BigDecimal totalSalePrice, BigDecimal penalty, BigDecimal mainServiceFee) {
    }

    public record Money(BigDecimal amount, String currency) {
    }
}
