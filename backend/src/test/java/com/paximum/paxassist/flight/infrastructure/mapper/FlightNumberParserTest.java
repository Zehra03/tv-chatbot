package com.paximum.paxassist.flight.infrastructure.mapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlightNumberParserTest {

    @Test
    void parse_returnsNullForNullInput() {
        FlightNumberParser.ParsedFlightNumber parsed = FlightNumberParser.parse(null);

        assertThat(parsed.airlineCode()).isNull();
        assertThat(parsed.number()).isNull();
    }

    @Test
    void parse_parsesValidFlightNumber() {
        FlightNumberParser.ParsedFlightNumber parsed = FlightNumberParser.parse("TK1979");

        assertThat(parsed.airlineCode()).isEqualTo("TK");
        assertThat(parsed.number()).isEqualTo("1979");
    }

    @Test
    void parse_parsesValidFlightNumberWithLettersAtEnd() {
        FlightNumberParser.ParsedFlightNumber parsed = FlightNumberParser.parse("TK1979A");

        assertThat(parsed.airlineCode()).isEqualTo("TK");
        assertThat(parsed.number()).isEqualTo("1979A");
    }

    @Test
    void parse_parsesValidFlightNumberWithNumberInAirline() {
        FlightNumberParser.ParsedFlightNumber parsed = FlightNumberParser.parse("8Q100");

        assertThat(parsed.airlineCode()).isEqualTo("8Q");
        assertThat(parsed.number()).isEqualTo("100");
    }

    @Test
    void parse_returnsOriginalIfNoMatch() {
        FlightNumberParser.ParsedFlightNumber parsed = FlightNumberParser.parse("INVALID_FLIGHT");

        assertThat(parsed.airlineCode()).isNull();
        assertThat(parsed.number()).isEqualTo("INVALID_FLIGHT");
    }

    @Test
    void parse_trimsInputBeforeParsing() {
        FlightNumberParser.ParsedFlightNumber parsed = FlightNumberParser.parse("  TK1979  ");

        assertThat(parsed.airlineCode()).isEqualTo("TK");
        assertThat(parsed.number()).isEqualTo("1979");
    }
}
