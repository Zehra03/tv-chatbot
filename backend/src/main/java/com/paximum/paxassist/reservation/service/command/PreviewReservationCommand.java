package com.paximum.paxassist.reservation.service.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.domain.TripType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Booking input to {@code previewReservation}. Validated at the HTTP boundary via Bean Validation
 * (the controller binds this with {@code @Valid}); the service then trusts the data. It carries
 * everything needed to (a) show a summary, (b) later drive the TourVisio transaction flow, and
 * (c) persist ticket 1's entities — so the whole confirm flow can run purely from the frozen snapshot.
 *
 * <p>{@code totalAmount}/{@code currency} are the price the user is committing to; there is no
 * re-verification against live Hotel/Flight availability (no such method exists yet — accepted risk,
 * see {@code ReservationService}).
 *
 * <p>At least one of {@link #hotel()} / {@link #flight()} must be present; the product type is derived
 * from which are present (never trusted from input). {@code userId} is injected server-side from the
 * authenticated principal via {@link #withUserId(Long)} — it is intentionally NOT validated on input.
 */
public record PreviewReservationCommand(
        Long userId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @PositiveOrZero BigDecimal totalAmount,
        String culture,
        @NotBlank @Size(max = 200) String leadGuestName,
        String reservationNote,
        String agencyReservationNumber,
        List<String> offerIds,
        @Valid List<AddOffer> additionalOffers,
        @NotEmpty @Valid List<Traveller> travellers,
        @Valid Customer customer,
        @Valid Hotel hotel,
        @Valid Flight flight) {

    /** Cross-field rule: a reservation must include at least a hotel or a flight (product type is derived, never trusted). */
    @AssertTrue(message = "A reservation must include at least a hotel or a flight")
    public boolean isAtLeastOneProductPresent() {
        return hotel != null || flight != null;
    }

    /** Returns a copy with the given owner id (the controller injects the authenticated user id here). */
    public PreviewReservationCommand withUserId(Long userId) {
        return new PreviewReservationCommand(userId, currency, totalAmount, culture, leadGuestName, reservationNote,
                agencyReservationNumber, offerIds, additionalOffers, travellers, customer, hotel, flight);
    }

    /** An additional service/offer to attach via AddServices (optional; primary offers go in {@link #offerIds()}). */
    public record AddOffer(String offerId, List<String> travellerIds) {
    }

    /** A traveller: superset of the fields needed for both the Passenger entity and the TourVisio traveller. */
    public record Traveller(
            String travellerId,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotNull PassengerType passengerType,
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
            @NotBlank String hotelName,
            String region,
            Short stars,
            String boardType,
            @NotNull LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @NotNull @Positive Short rooms,
            @NotNull @Positive Short adults,
            @PositiveOrZero Short children,
            String nationality,
            @NotNull @PositiveOrZero BigDecimal price,
            @NotBlank @Size(min = 3, max = 3) String currency) {
    }

    /** Booked flight snapshot; mirrors {@code FlightReservationDetails}. */
    public record Flight(
            @NotBlank String origin,
            @NotBlank String destination,
            String airline,
            @NotNull TripType tripType,
            @NotNull OffsetDateTime departTime,
            OffsetDateTime arriveTime,
            OffsetDateTime returnDepartTime,
            OffsetDateTime returnArriveTime,
            @PositiveOrZero Short stops,
            String baggage,
            @NotNull @Positive Short passengerCount,
            @NotNull @PositiveOrZero BigDecimal price,
            @NotBlank @Size(min = 3, max = 3) String currency) {
    }
}
