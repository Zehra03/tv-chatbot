package com.paximum.paxassist.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.SetReservationInfoRequest;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;

/**
 * Covers the traveller birth-date handling: TourVisio rejects a child/infant with no date of birth
 * ({@code ParameterCanNotBeNull}), and the booking form only collects an age for them, so the mapper
 * derives a nominal DOB from the age. Adults (which already book fine without a DOB) must stay untouched,
 * and an explicitly provided birth date must always win.
 */
class ReservationTourVisioRequestMapperTest {

    private static final DateTimeFormatter TV_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ReservationTourVisioRequestMapper mapper = new ReservationTourVisioRequestMapper();

    private PreviewReservationCommand.Traveller traveller(PassengerType type, Integer age, LocalDate birthDate) {
        return new PreviewReservationCommand.Traveller(null, "Max", "Yılmaz", type, age,
                null, null, null, false, null, null, birthDate, null, null, null, null);
    }

    private SetReservationInfoRequest.Traveller mapFirst(PreviewReservationCommand.Traveller traveller) {
        PreviewReservationCommand command = new PreviewReservationCommand(null, null, "EUR", BigDecimal.TEN, null,
                "Lead", null, null, null, null, List.of(traveller), null, null, null);
        return mapper.toSetReservationInfoRequest(command, "txn-1").travellers().get(0);
    }

    @Test
    void derivesBirthDateForChildFromAgeWhenMissing() {
        String mapped = mapFirst(traveller(PassengerType.CHILD, 7, null)).birthDate();

        String expected = LocalDate.now().minusYears(7).atStartOfDay().format(TV_DATE_TIME);
        assertThat(mapped).isEqualTo(expected);
    }

    @Test
    void derivesBirthDateForInfantFromAgeWhenMissing() {
        String mapped = mapFirst(traveller(PassengerType.INFANT, 1, null)).birthDate();

        String expected = LocalDate.now().minusYears(1).atStartOfDay().format(TV_DATE_TIME);
        assertThat(mapped).isEqualTo(expected);
    }

    @Test
    void leavesAdultBirthDateNullWhenMissing() {
        // Adults book fine without a DOB — the derivation must not change their (working) request.
        assertThat(mapFirst(traveller(PassengerType.ADULT, 34, null)).birthDate()).isNull();
    }

    @Test
    void keepsExplicitBirthDateWhenProvided() {
        String mapped = mapFirst(traveller(PassengerType.CHILD, 7, LocalDate.of(2019, 5, 20))).birthDate();

        assertThat(mapped).isEqualTo("2019-05-20T00:00:00");
    }
}
