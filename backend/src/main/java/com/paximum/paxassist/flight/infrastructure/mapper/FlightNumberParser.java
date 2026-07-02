package com.paximum.paxassist.flight.infrastructure.mapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FlightNumberParser {

    private static final Pattern FLIGHT_NUMBER_PATTERN = Pattern.compile("^([A-Za-z0-9]{2})(\\d{1,4}[A-Za-z]?)$");

    private FlightNumberParser() {
    }

    public static ParsedFlightNumber parse(String flightNo) {
        if (flightNo == null) {
            return new ParsedFlightNumber(null, null);
        }
        Matcher matcher = FLIGHT_NUMBER_PATTERN.matcher(flightNo.trim());
        if (matcher.matches()) {
            return new ParsedFlightNumber(matcher.group(1), matcher.group(2));
        }
        return new ParsedFlightNumber(null, flightNo);
    }

    public record ParsedFlightNumber(String airlineCode, String number) {
    }
}