package com.paximum.paxassist.reservation.service.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.domain.TripType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The booking command's Bean Validation rules, exercised straight against a {@link Validator} — the
 * controller only binds them with {@code @Valid} (see {@code ReservationControllerValidationTest} for
 * the HTTP side). These rules are the last line before a snapshot is frozen and later handed to
 * TourVisio, where a violation would otherwise surface only AFTER confirm, with the preview consumed.
 */
class PreviewReservationCommandTest {

    private static final String NO_ADULT = "Rezervasyonda en az bir yetişkin yolcu bulunmalıdır (yalnız çocuk seyahat edemez)";
    private static final String LEAD_NOT_ADULT = "Ana misafir (iletişim kurulacak yolcu) yetişkin olmalıdır";
    private static final String AGE_TYPE_MISMATCH = "Yolcu yaşı seçilen tiple uyumlu değil (yetişkin: 18+, çocuk: 3-17, bebek: 0-2)";
    private static final String COUNT_MISMATCH = "Yolcu sayısı seçilen ürünün kişi sayısıyla eşleşmiyor";
    private static final String INFANT_NEEDS_FLIGHT = "Bebek (infant) yolcu yalnızca uçuş içeren rezervasyonlarda eklenebilir";
    private static final String INFANT_NEEDS_ADULT = "Her bebek (infant) için bir yetişkin gerekir; bebek sayısı yetişkin sayısını aşamaz";
    private static final String LEAD_NOT_CONTACTABLE = "Ana misafirin e-posta ve telefon bilgisi zorunludur";
    private static final String CHILD_NOT_DATABLE = "Çocuk/bebek yolcular için yaş veya doğum tarihi gereklidir";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** Carries contact details on every traveller: the lead must be reachable (e-mail + phone). */
    private PreviewReservationCommand.Traveller traveller(String name, PassengerType type, Integer age,
                                                          boolean leader) {
        return new PreviewReservationCommand.Traveller(null, name, "Yılmaz", type, age,
                null, "ada@example.com", "+905551112233", leader, null, null, null, null, null, null, null);
    }

    private PreviewReservationCommand.Traveller adult(String name) {
        return traveller(name, PassengerType.ADULT, 34, false);
    }

    private PreviewReservationCommand.Traveller child(String name) {
        return traveller(name, PassengerType.CHILD, 8, false);
    }

    /** Hotel snapshot sized for the given party. Dates are relative — checkIn carries @FutureOrPresent. */
    private PreviewReservationCommand.Hotel hotelFor(int adults, int children) {
        LocalDate checkIn = LocalDate.now().plusDays(30);
        return new PreviewReservationCommand.Hotel(
                "Grand Antalya", "Antalya", (short) 5, "AI", checkIn, checkIn.plusDays(4),
                (short) 1, (short) adults, (short) children, "TR", new BigDecimal("100"), "EUR");
    }

    private PreviewReservationCommand.Flight flightFor(int passengers) {
        return new PreviewReservationCommand.Flight(
                "IST", "AYT", "TK", TripType.ONE_WAY, OffsetDateTime.now().plusDays(30), null, null, null,
                (short) 0, "20kg", (short) passengers, new BigDecimal("100"), "EUR");
    }

    private PreviewReservationCommand command(List<PreviewReservationCommand.Traveller> travellers,
                                              PreviewReservationCommand.Hotel hotel,
                                              PreviewReservationCommand.Flight flight) {
        return new PreviewReservationCommand(1L, null, "EUR", new BigDecimal("100"), null, "Ada Yılmaz",
                null, null, null, null, travellers, null, hotel, flight);
    }

    private Set<String> violations(PreviewReservationCommand command) {
        return validator.validate(command).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    // ── At least one adult (P0: unaccompanied minor) ─────────────────────────────────────────

    @Test
    void childOnlyBooking_isRejected() {
        assertThat(violations(command(List.of(child("Max")), hotelFor(0, 1), null)))
                .contains(NO_ADULT);
    }

    @Test
    void adultAccompanyingAChild_isAccepted() {
        assertThat(violations(command(List.of(adult("Ada"), child("Max")), hotelFor(1, 1), null)))
                .isEmpty();
    }

    @Test
    void anEighteenYearOldIsAnAdultAndMayTravelAlone() {
        assertThat(violations(command(
                List.of(traveller("Efe", PassengerType.ADULT, 18, true)), null, flightFor(1))))
                .isEmpty();
    }

    // ── Lead traveller must be an adult (contact details land on them) ───────────────────────

    @Test
    void childFlaggedAsLeader_isRejected() {
        assertThat(violations(command(
                List.of(traveller("Max", PassengerType.CHILD, 8, true), adult("Ada")), hotelFor(1, 1), null)))
                .contains(LEAD_NOT_ADULT);
    }

    @Test
    void childFirstInTheListWithNoLeaderFlag_isRejected() {
        // The frontend writes the contact e-mail/phone onto passenger 0, so first == lead when unflagged.
        assertThat(violations(command(List.of(child("Max"), adult("Ada")), hotelFor(1, 1), null)))
                .contains(LEAD_NOT_ADULT);
    }

    @Test
    void adultFirstWithAChildBehind_isAccepted() {
        assertThat(violations(command(List.of(adult("Ada"), child("Max")), hotelFor(1, 1), null)))
                .isEmpty();
    }

    // ── Non-adult travellers must be datable (age or DOB) for TourVisio ──────────────────────

    @Test
    void childWithNeitherAgeNorBirthDate_isRejected() {
        // Both null → the request mapper has nothing to send TourVisio as the child's DOB, which it
        // rejects with ParameterCanNotBeNull. Catch it at the boundary instead of after confirm.
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Max", PassengerType.CHILD, null, false)), hotelFor(1, 1), null)))
                .contains(CHILD_NOT_DATABLE);
    }

    @Test
    void childWithAnAge_isAccepted() {
        assertThat(violations(command(List.of(adult("Ada"), child("Max")), hotelFor(1, 1), null)))
                .doesNotContain(CHILD_NOT_DATABLE);
    }

    @Test
    void childWithABirthDateButNoAge_isAccepted() {
        PreviewReservationCommand.Traveller kid = new PreviewReservationCommand.Traveller(
                null, "Max", "Yılmaz", PassengerType.CHILD, null,
                null, "ada@example.com", "+905551112233", false, null, null,
                LocalDate.now().minusYears(8), null, null, null, null);
        assertThat(violations(command(List.of(adult("Ada"), kid), hotelFor(1, 1), null)))
                .doesNotContain(CHILD_NOT_DATABLE);
    }

    @Test
    void adultWithoutAnAgeOrBirthDate_isAccepted() {
        // The rule targets non-adults only; an adult still books without a DOB.
        assertThat(violations(command(
                List.of(traveller("Ada", PassengerType.ADULT, null, true)), hotelFor(1, 0), null)))
                .doesNotContain(CHILD_NOT_DATABLE);
    }

    // ── The booking must be contactable ──────────────────────────────────────────────────────

    /** Same as traveller(), but with the contact fields the form collects left out. */
    private PreviewReservationCommand.Traveller withContact(String name, String email, String phone) {
        return new PreviewReservationCommand.Traveller(null, name, "Yılmaz", PassengerType.ADULT, 34,
                null, email, phone, true, null, null, null, null, null, null, null);
    }

    @Test
    void leadTravellerWithoutAnEmail_isRejected() {
        // Without contact details the voucher, a schedule change and a cancellation notice have nowhere
        // to go — and the booking would still confirm.
        assertThat(violations(command(List.of(withContact("Ada", null, "+905551112233")), hotelFor(1, 0), null)))
                .contains(LEAD_NOT_CONTACTABLE);
    }

    @Test
    void leadTravellerWithoutAPhone_isRejected() {
        assertThat(violations(command(List.of(withContact("Ada", "ada@example.com", null)), hotelFor(1, 0), null)))
                .contains(LEAD_NOT_CONTACTABLE);
    }

    @Test
    void blankContactFields_countAsMissing() {
        assertThat(violations(command(List.of(withContact("Ada", "  ", "  ")), hotelFor(1, 0), null)))
                .contains(LEAD_NOT_CONTACTABLE);
    }

    @Test
    void leadTravellerWithBothContactFields_isAccepted() {
        assertThat(violations(command(
                List.of(withContact("Ada", "ada@example.com", "+905551112233")), hotelFor(1, 0), null)))
                .isEmpty();
    }

    @Test
    void nonLeadTravellersNeedNoContactDetails() {
        // A child does not carry a phone; only the lead is the contact person.
        PreviewReservationCommand.Traveller lead = withContact("Ada", "ada@example.com", "+905551112233");
        PreviewReservationCommand.Traveller kid = new PreviewReservationCommand.Traveller(
                null, "Max", "Yılmaz", PassengerType.CHILD, 8, null, null, null,
                false, null, null, null, null, null, null, null);

        assertThat(violations(command(List.of(lead, kid), hotelFor(1, 1), null))).isEmpty();
    }

    // ── Age / type cross-check ───────────────────────────────────────────────────────────────

    @Test
    void grownUpDeclaredAsChild_isRejected() {
        // Child-pricing exploit: a 45-year-old sent as CHILD.
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Max", PassengerType.CHILD, 45, false)), hotelFor(1, 1), null)))
                .contains(AGE_TYPE_MISMATCH);
    }

    @Test
    void toddlerDeclaredAsAdult_isRejected() {
        assertThat(violations(command(
                List.of(traveller("Max", PassengerType.ADULT, 3, true)), hotelFor(1, 0), null)))
                .contains(AGE_TYPE_MISMATCH);
    }

    @Test
    void ageIsOptional_soATravellerWithoutOneIsNotCrossChecked() {
        assertThat(violations(command(
                List.of(traveller("Ada", PassengerType.ADULT, null, true)), hotelFor(1, 0), null)))
                .isEmpty();
    }

    @Test
    void seventeenIsAChildAndEighteenIsAnAdult() {
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Max", PassengerType.CHILD, 17, false)), hotelFor(1, 1), null)))
                .isEmpty();
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Efe", PassengerType.CHILD, 18, false)), hotelFor(1, 1), null)))
                .contains(AGE_TYPE_MISMATCH);
    }

    // ── Infant: a flight-only fare type (lap infant), never a hotel passenger type ───────────
    //
    // Booking.com models hotel children by exact age and prices them from age bands — there is no
    // infant type on the hotel side (it is a cot/extra-bed policy). Infant is an airline concept: a
    // lap infant carried on an adult's ticket, hence at most one per adult.

    private PreviewReservationCommand.Traveller infant(String name, int age) {
        return traveller(name, PassengerType.INFANT, age, false);
    }

    @Test
    void infantOnAFlight_isAccepted() {
        assertThat(violations(command(
                List.of(adult("Ada"), infant("Bebek", 1)), null, flightFor(2))))
                .isEmpty();
    }

    @Test
    void infantOnAHotelOnlyBooking_isRejected() {
        // An under-2 in a hotel booking is a CHILD priced from their age, not an infant fare.
        assertThat(violations(command(
                List.of(adult("Ada"), infant("Bebek", 1)), hotelFor(1, 1), null)))
                .contains(INFANT_NEEDS_FLIGHT);
    }

    @Test
    void infantOnACombinedBooking_isAcceptedBecauseAFlightIsPresent() {
        assertThat(violations(command(
                List.of(adult("Ada"), infant("Bebek", 1)), hotelFor(1, 1), flightFor(2))))
                .isEmpty();
    }

    @Test
    void moreInfantsThanAdults_isRejected() {
        // One adult cannot carry two lap infants.
        assertThat(violations(command(
                List.of(adult("Ada"), infant("Bebek", 1), infant("İkiz", 1)), null, flightFor(3))))
                .contains(INFANT_NEEDS_ADULT);
    }

    @Test
    void oneInfantPerAdult_isAccepted() {
        assertThat(violations(command(
                List.of(adult("Ada"), adult("Efe"), infant("Bebek", 1), infant("İkiz", 0)), null, flightFor(4))))
                .isEmpty();
    }

    @Test
    void infantOnlyBooking_isRejectedForHavingNoAdult() {
        assertThat(violations(command(List.of(infant("Bebek", 1)), null, flightFor(1))))
                .contains(NO_ADULT);
    }

    @Test
    void ageBandBoundaries_threeIsAChildAndTwoIsAnInfant() {
        // Bands: infant 0-2, child 3-17. So 2 is still an infant and 3 is the first child age.
        assertThat(violations(command(
                List.of(adult("Ada"), infant("Bebek", 2)), null, flightFor(2))))
                .isEmpty();
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Max", PassengerType.CHILD, 3, false)), null, flightFor(2))))
                .isEmpty();
        // A 2-year-old is not a child, and a 3-year-old is not an infant.
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Bebek", PassengerType.CHILD, 2, false)), null, flightFor(2))))
                .contains(AGE_TYPE_MISMATCH);
        assertThat(violations(command(
                List.of(adult("Ada"), infant("Max", 3)), null, flightFor(2))))
                .contains(AGE_TYPE_MISMATCH);
    }

    @Test
    void underTwoDeclaredAsChildOnAHotelOnlyBooking_isAccepted() {
        // The one case PassengerType's own band can't express: no INFANT type exists for a
        // flightless booking, so an under-2 is booked as a CHILD instead (see
        // infantOnAHotelOnlyBooking_isRejected — INFANT itself is still refused here).
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Bebek", PassengerType.CHILD, 1, false)), hotelFor(1, 1), null)))
                .doesNotContain(AGE_TYPE_MISMATCH);
    }

    @Test
    void underTwoDeclaredAsChildOnAFlightOrCombinedBooking_isStillRejected() {
        // The hotel-only exception must not leak into a booking that HAS a flight: there, an
        // under-3 still belongs to INFANT, matching ageBandBoundaries_threeIsAChildAndTwoIsAnInfant.
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Bebek", PassengerType.CHILD, 1, false)), null, flightFor(2))))
                .contains(AGE_TYPE_MISMATCH);
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Bebek", PassengerType.CHILD, 1, false)), hotelFor(1, 1), flightFor(2))))
                .contains(AGE_TYPE_MISMATCH);
    }

    // ── Traveller count vs the priced snapshot ───────────────────────────────────────────────

    @Test
    void moreTravellersThanTheHotelWasPricedFor_isRejected() {
        assertThat(violations(command(
                List.of(adult("Ada"), adult("Efe"), adult("Zehra")), hotelFor(2, 0), null)))
                .contains(COUNT_MISMATCH);
    }

    @Test
    void fewerTravellersThanTheHotelWasPricedFor_isRejected() {
        assertThat(violations(command(List.of(adult("Ada")), hotelFor(3, 0), null)))
                .contains(COUNT_MISMATCH);
    }

    @Test
    void hotelCountIncludesChildren() {
        assertThat(violations(command(
                List.of(adult("Ada"), adult("Efe"), child("Max")), hotelFor(2, 1), null)))
                .isEmpty();
    }

    @Test
    void flightPassengerCountMustMatchToo() {
        assertThat(violations(command(List.of(adult("Ada"), adult("Efe")), null, flightFor(3))))
                .contains(COUNT_MISMATCH);
        assertThat(violations(command(List.of(adult("Ada"), adult("Efe")), null, flightFor(2))))
                .isEmpty();
    }

    @Test
    void combinedBookingMustSatisfyBothCounts() {
        // Hotel priced for 2, flight for 3 — no traveller count can satisfy both, so it is always invalid.
        assertThat(violations(command(
                List.of(adult("Ada"), adult("Efe")), hotelFor(2, 0), flightFor(3))))
                .contains(COUNT_MISMATCH);
    }

    // ── Field-level constraints ──────────────────────────────────────────────────────────────

    @Test
    void invalidTravellerEmail_isRejected() {
        PreviewReservationCommand.Traveller bad = new PreviewReservationCommand.Traveller(
                null, "Ada", "Yılmaz", PassengerType.ADULT, 34, null, "not-an-email", null,
                true, null, null, null, null, null, null, null);

        assertThat(violations(command(List.of(bad), hotelFor(1, 0), null))).isNotEmpty();
    }

    @Test
    void pastCheckIn_isRejected() {
        LocalDate past = LocalDate.now().minusDays(1);
        PreviewReservationCommand.Hotel hotel = new PreviewReservationCommand.Hotel(
                "Grand Antalya", "Antalya", (short) 5, "AI", past, past.plusDays(4),
                (short) 1, (short) 1, (short) 0, "TR", new BigDecimal("100"), "EUR");

        assertThat(violations(command(List.of(adult("Ada")), hotel, null))).isNotEmpty();
    }

    @Test
    void checkOutBeforeCheckIn_isRejected() {
        LocalDate checkIn = LocalDate.now().plusDays(30);
        PreviewReservationCommand.Hotel hotel = new PreviewReservationCommand.Hotel(
                "Grand Antalya", "Antalya", (short) 5, "AI", checkIn, checkIn.minusDays(2),
                (short) 1, (short) 1, (short) 0, "TR", new BigDecimal("100"), "EUR");

        assertThat(violations(command(List.of(adult("Ada")), hotel, null)))
                .contains("Çıkış tarihi giriş tarihinden sonra olmalıdır");
    }

    @Test
    void emptyTravellerList_reportsOnlyTheNotEmptyViolation() {
        // The party rules must not pile misleading extra messages onto an obviously empty list.
        Set<String> messages = violations(command(List.of(), hotelFor(1, 0), null));

        assertThat(messages).doesNotContain(NO_ADULT, LEAD_NOT_ADULT, COUNT_MISMATCH);
        assertThat(messages).isNotEmpty();
    }
}
