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
    private static final String AGE_TYPE_MISMATCH = "Yolcu yaşı seçilen tiple uyumlu değil (yetişkin: 12 ve üzeri, çocuk: 12 yaş altı)";
    private static final String COUNT_MISMATCH = "Yolcu sayısı seçilen ürünün kişi sayısıyla eşleşmiyor";

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

    private PreviewReservationCommand.Traveller traveller(String name, PassengerType type, Integer age,
                                                          boolean leader) {
        return new PreviewReservationCommand.Traveller(null, name, "Yılmaz", type, age,
                null, "ada@example.com", null, leader, null, null, null, null, null, null, null);
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
        return new PreviewReservationCommand(1L, "EUR", new BigDecimal("100"), null, "Ada Yılmaz",
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
    void aTwelveYearOldBookedAsAdult_mayTravelAlone() {
        // Flight age policy: 12+ counts as an adult, so a lone 12-year-old must be booked as ADULT.
        assertThat(violations(command(
                List.of(traveller("Efe", PassengerType.ADULT, 12, true)), null, flightFor(1))))
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
    void elevenIsAChildAndTwelveIsAnAdult() {
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Max", PassengerType.CHILD, 11, false)), hotelFor(1, 1), null)))
                .isEmpty();
        assertThat(violations(command(
                List.of(adult("Ada"), traveller("Efe", PassengerType.CHILD, 12, false)), hotelFor(1, 1), null)))
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
