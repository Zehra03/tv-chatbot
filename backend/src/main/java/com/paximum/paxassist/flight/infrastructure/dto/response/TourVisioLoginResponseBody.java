package com.paximum.paxassist.flight.infrastructure.dto.response;

// TourVisio login body. expiresOn is the ISO-8601 instant the returned token stops being accepted;
// the token provider re-logs in before it to avoid serving an expired token (whose expiry TourVisio
// reports as a 200 "TokenRequired" business failure, not a 401 — so it can't be caught reactively).
public record TourVisioLoginResponseBody(String token, String expiresOn) {
}
