package com.paximum.paxassist.flight.dto;

import com.paximum.paxassist.flight.domain.TripType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FlightSearchRequestDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenDepartDateIsBeyondMaxDate_thenValidationFailsWithOutOfCalendarRange() {
        LocalDate farFuture = LocalDate.now().plusDays(400);
        FlightSearchRequestDto request = new FlightSearchRequestDto(
                "IST",
                "AYT",
                farFuture,
                null,
                TripType.ONE_WAY,
                new PassengerCountDto(1, 0, 0),
                "TRY",
                true,
                null
        );

        var violations = validator.validate(request);

        var departViolation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("departDate"))
                .findFirst();

        assertThat(departViolation).isPresent();
        assertThat(departViolation.get().getMessage()).isEqualTo("OUT_OF_CALENDAR_RANGE");
    }
}
