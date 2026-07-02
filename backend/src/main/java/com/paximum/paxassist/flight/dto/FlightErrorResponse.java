package com.paximum.paxassist.flight.dto;

import java.util.List;

public record FlightErrorResponse(String message, List<String> details) {
}
