package com.paximum.paxassist.reservation.service.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.domain.TripType;

/**
 * Already-validated booking input to {@code previewReservation} (ticket 4/5 own the HTTP + validation
 * layers; this service trusts the data). It carries everything needed to (a) show a summary, (b) later
 * drive the TourVisio transaction flow, and (c) persist ticket 1's entities — so the whole confirm
 * flow can run purely from the frozen snapshot.
 *
 * <p>{@code totalAmount}/{@code currency} are the price the user is committing to; there is no
 * re-verification against live Hotel/Flight availability (no such method exists yet — accepted risk,
 * see {@code ReservationService}).
 *
 * <p>At least one of {@link #hotel()} / {@link #flight()} must be present; the product type is derived
 * from which are present (never trusted from input).
 */
public record PreviewReservationCommand(
        Long userId,
        String currency,
        BigDecimal totalAmount,
        String culture,
        String leadGuestName,
        String reservationNote,
        String agencyReservationNumber,
        List<String> offerIds,
        List<AddOffer> additionalOffers,
        List<Traveller> travellers,
        Customer customer,
        Hotel hotel,
        Flight flight) {

    /** An additional service/offer to attach via AddServices (optional; primary offers go in {@link #offerIds()}). */
    public record AddOffer(String offerId, List<String> travellerIds) {
    }

    /** A traveller: superset of the fields needed for both the Passenger entity and the TourVisio traveller. */
    public record Traveller(
            String travellerId,
            String firstName,
            String lastName,
            PassengerType passengerType,
            Integer age,
            String nationalityCode,
            String email,
            String phone,
            boolean leader,
            Integer title,
            Integer gender,
            LocalDate birthDate,
            String identityNumber,
            Passport passport,
            ContactPhone contactPhone,
            Address address) {
    }

    public record Passport(String serial, String number, LocalDate expireDate, LocalDate issueDate, String citizenshipCountryCode) {
    }

    public record ContactPhone(String countryCode, String areaCode, String phoneNumber) {
    }

    public record Address(String email, String line, String zipCode, String cityId, String cityName, String countryId, String countryName) {
    }

    public record Customer(
            boolean company,
            String name,
            String surname,
            LocalDate birthDate,
            String identityNumber,
            Integer title,
            String email,
            String phone,
            String line,
            String zipCode,
            String cityName,
            String countryName,
            String taxOffice,
            String taxNumber) {
    }

    /** Booked hotel snapshot; mirrors {@code HotelReservationDetails}. */
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

    /** Booked flight snapshot; mirrors {@code FlightReservationDetails}. */
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
}
