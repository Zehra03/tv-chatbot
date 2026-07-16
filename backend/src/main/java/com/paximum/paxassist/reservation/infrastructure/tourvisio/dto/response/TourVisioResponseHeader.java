package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The TourVisio-wide response envelope header, confirmed from the cancellation
 * samples and treated as the actual granular error-code contract (not a
 * pricesearch-only pattern).
 *
 * <p>Callers must classify on {@link TourVisioMessage#code()} — not merely on
 * {@link #success()} — because {@code code} (e.g. {@code "OperationCompleted"})
 * is the granular signal the acceptance tests key on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioResponseHeader(
        String requestId,
        boolean success,
        List<TourVisioMessage> messages) {

    /** First message code if present, else {@code null}. Convenience for logging/branching. */
    public String primaryCode() {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(0).code();
    }

    public boolean hasCode(String code) {
        if (messages == null) {
            return false;
        }
        return messages.stream().anyMatch(m -> m.code() != null && m.code().equalsIgnoreCase(code));
    }

    /**
     * TourVisio {@code messageType} 4 = a warning that needs explicit user confirmation
     * (e.g. {@code DuplicateReservationFound}), which can appear even when {@link #success()} is
     * {@code true}. A caller must NOT treat such a response as plain success — surface it to the
     * user for confirmation before continuing the flow.
     */
    public static final int MESSAGE_TYPE_CONFIRMATION_REQUIRED = 4;

    /** True if any message is a {@link #MESSAGE_TYPE_CONFIRMATION_REQUIRED} warning. */
    public boolean requiresConfirmation() {
        if (messages == null) {
            return false;
        }
        return messages.stream()
                .anyMatch(m -> m.messageType() != null && m.messageType() == MESSAGE_TYPE_CONFIRMATION_REQUIRED);
    }
}
