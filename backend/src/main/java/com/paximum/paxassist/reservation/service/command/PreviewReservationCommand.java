package com.paximum.paxassist.reservation.service.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.domain.TripType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
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

    /** Age bands (product decision): infant 0–2, child 3–17, adult 18+. */
    private static final int ADULT_MIN_AGE = 18;

    /** Inclusive upper bound of the infant band — a lap infant is 0–2. */
    private static final int INFANT_MAX_AGE = 2;

    /** Cross-field rule: a reservation must include at least a hotel or a flight (product type is derived, never trusted). */
    @AssertTrue(message = "A reservation must include at least a hotel or a flight")
    public boolean isAtLeastOneProductPresent() {
        return hotel != null || flight != null;
    }

    /**
     * Cross-field rule: at least one ADULT traveller. A child-only (unaccompanied minor) booking is not
     * sellable — TourVisio rejects it during the transaction flow, i.e. only AFTER the confirm button,
     * once the preview has already been consumed by the atomic claim. Reject it at the boundary instead.
     *
     * <p>Null/empty lists return true so {@code @NotEmpty} raises the single accurate violation.
     */
    @AssertTrue(message = "Rezervasyonda en az bir yetişkin yolcu bulunmalıdır (yalnız çocuk seyahat edemez)")
    public boolean isAdultTravellerPresent() {
        if (travellers == null || travellers.isEmpty()) {
            return true;
        }
        return travellers.stream()
                .anyMatch(t -> t != null && t.passengerType() == PassengerType.ADULT);
    }

    /**
     * Cross-field rule: the lead traveller must be an adult. The frontend writes the contact e-mail and
     * phone onto the first traveller, so without this a 7-year-old could end up as the booking's lead
     * guest and contact person.
     *
     * <p>The lead is the one flagged {@code leader}, or the first traveller when none is flagged —
     * matching how the entity mapper and the TourVisio request mapper pick it.
     */
    @AssertTrue(message = "Ana misafir (iletişim kurulacak yolcu) yetişkin olmalıdır")
    public boolean isLeadTravellerAnAdult() {
        Traveller lead = leadTraveller();
        return lead == null || lead.passengerType() == null || lead.passengerType() == PassengerType.ADULT;
    }

    /**
     * Cross-field rule: the booking must be contactable — the lead traveller carries an e-mail AND a
     * phone. Both were optional, so a booking could be confirmed with no way to reach the guest at all:
     * the voucher, any schedule change and any cancellation notice have nowhere to go.
     *
     * <p>The e-mail's format is checked by {@code @Email} on the field; this rule is about presence, on
     * the one traveller that actually holds the contact details (the lead — see
     * {@link #isLeadTravellerAnAdult()}). Non-lead travellers stay optional: a child does not need a phone.
     */
    @AssertTrue(message = "Ana misafirin e-posta ve telefon bilgisi zorunludur")
    public boolean isLeadTravellerContactable() {
        Traveller lead = leadTraveller();
        if (lead == null) {
            return true;
        }
        return isPresent(lead.email()) && isPresent(lead.phone());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /** The traveller flagged {@code leader}, or the first one — how the mappers pick the lead guest. */
    private Traveller leadTraveller() {
        if (travellers == null || travellers.isEmpty()) {
            return null;
        }
        return travellers.stream()
                .filter(t -> t != null && t.leader())
                .findFirst()
                .orElse(travellers.get(0));
    }

    /**
     * Cross-field rule: a stated age must agree with the declared passenger type — ADULT 18+,
     * CHILD 3–17, INFANT 0–2. Without it, {@code passengerType=CHILD, age=45} (child pricing for an
     * adult) or {@code ADULT, age=3} passes every layer and reaches the DB and TourVisio.
     * Age is optional; travellers without one are not checked here.
     */
    @AssertTrue(message = "Yolcu yaşı seçilen tiple uyumlu değil (yetişkin: 18+, çocuk: 3-17, bebek: 0-2)")
    public boolean isTravellerAgeConsistentWithType() {
        if (travellers == null) {
            return true;
        }
        return travellers.stream()
                .filter(t -> t != null && t.age() != null && t.passengerType() != null)
                .allMatch(t -> switch (t.passengerType()) {
                    case ADULT -> t.age() >= ADULT_MIN_AGE;
                    case CHILD -> t.age() > INFANT_MAX_AGE && t.age() < ADULT_MIN_AGE;
                    case INFANT -> t.age() <= INFANT_MAX_AGE;
                });
    }

    /**
     * Cross-field rule: an INFANT is an airline fare type (a lap infant on an adult's ticket), so a
     * booking without a flight cannot carry one. Hotels have no infant type — they price children from
     * their exact age — so an under-2 in a hotel-only booking is booked as a CHILD.
     */
    @AssertTrue(message = "Bebek (infant) yolcu yalnızca uçuş içeren rezervasyonlarda eklenebilir")
    public boolean isInfantOnlyOnAFlightBooking() {
        if (travellers == null || flight != null) {
            return true;
        }
        return travellers.stream()
                .noneMatch(t -> t != null && t.passengerType() == PassengerType.INFANT);
    }

    /**
     * Cross-field rule: an infant travels on an adult's lap, so it cannot outnumber the adults — one
     * adult cannot carry two lap infants. (The ≥1-adult rule above already covers the infant-only case.)
     */
    @AssertTrue(message = "Her bebek (infant) için bir yetişkin gerekir; bebek sayısı yetişkin sayısını aşamaz")
    public boolean isEachInfantAccompaniedByAnAdult() {
        if (travellers == null || travellers.isEmpty()) {
            return true;
        }
        long infants = travellers.stream()
                .filter(t -> t != null && t.passengerType() == PassengerType.INFANT)
                .count();
        long adults = travellers.stream()
                .filter(t -> t != null && t.passengerType() == PassengerType.ADULT)
                .count();
        return infants <= adults;
    }

    /**
     * Cross-field rule: the traveller list must match the party size frozen in the product snapshot —
     * hotel {@code adults + children}, flight {@code passengerCount}. The snapshot is what was priced
     * and what TourVisio will be asked to book, so a request carrying five travellers for a
     * three-person offer is priced for three and would fail (or silently under-charge) downstream.
     *
     * <p>A combined booking must satisfy both counts. Null counts are left to their own {@code @NotNull}.
     */
    @AssertTrue(message = "Yolcu sayısı seçilen ürünün kişi sayısıyla eşleşmiyor")
    public boolean isTravellerCountMatchingTheProduct() {
        if (travellers == null || travellers.isEmpty()) {
            return true;
        }
        int count = travellers.size();
        if (hotel != null && hotel.adults() != null) {
            int expected = hotel.adults() + (hotel.children() == null ? 0 : hotel.children());
            if (count != expected) {
                return false;
            }
        }
        return flight == null || flight.passengerCount() == null || count == flight.passengerCount();
    }

    /** Returns a copy with the given owner id (the controller injects the authenticated user id here). */
    public PreviewReservationCommand withUserId(Long userId) {
        return new PreviewReservationCommand(userId, currency, totalAmount, culture, leadGuestName, reservationNote,
                agencyReservationNumber, offerIds, additionalOffers, travellers, customer, hotel, flight);
    }

    /**
     * Returns a copy carrying TourVisio's own price. The preview re-prices the offer against the provider
     * and freezes THIS copy, so the amount that reaches confirm and the DB is the one the provider will
     * charge — never the figure the client sent.
     */
    public PreviewReservationCommand withTotalAmount(BigDecimal amount, String amountCurrency) {
        return new PreviewReservationCommand(userId, amountCurrency, amount, culture, leadGuestName, reservationNote,
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
            @PositiveOrZero @Max(120) Integer age,
            String nationalityCode,
            @Email String email,
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
            @NotNull @FutureOrPresent LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @NotNull @Positive Short rooms,
            @NotNull @Positive Short adults,
            @PositiveOrZero Short children,
            String nationality,
            @NotNull @PositiveOrZero BigDecimal price,
            @NotBlank @Size(min = 3, max = 3) String currency) {

        /**
         * Cross-field rule: each room needs at least one adult, so rooms must not exceed adults
         * (mirrors the frontend search-form guard). Null cases are left to {@code @NotNull} on the
         * fields so this check does not raise a second, misleading violation.
         */
        @AssertTrue(message = "Rooms cannot exceed the number of adults (each room needs at least one adult)")
        public boolean isRoomsWithinAdults() {
            return rooms == null || adults == null || rooms <= adults;
        }

        /**
         * Cross-field rule: check-out must be strictly after check-in — otherwise the stay is zero or
         * negative nights, which TourVisio rejects mid-transaction rather than at the boundary.
         */
        @AssertTrue(message = "Çıkış tarihi giriş tarihinden sonra olmalıdır")
        public boolean isCheckOutAfterCheckIn() {
            return checkIn == null || checkOut == null || checkOut.isAfter(checkIn);
        }
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
