package com.paximum.paxassist.hotel.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HotelSearchApiRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenCheckInIsBeyondMaxDate_thenValidationFailsWithOutOfCalendarRange() {
        LocalDate farFuture = LocalDate.now().plusDays(400);
        HotelSearchApiRequest request = new HotelSearchApiRequest(
                "Antalya",
                farFuture,
                farFuture.plusDays(3),
                2,
                0,
                1,
                java.util.Collections.<Integer>emptyList(),
                "TR",
                "TRY"
        );

        var violations = validator.validate(request);

        var checkInViolation = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("checkIn"))
                .findFirst();

        assertThat(checkInViolation).isPresent();
        assertThat(checkInViolation.get().getMessage()).isEqualTo("OUT_OF_CALENDAR_RANGE");
    }
}
