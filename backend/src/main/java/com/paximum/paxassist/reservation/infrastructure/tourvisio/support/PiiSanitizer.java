package com.paximum.paxassist.reservation.infrastructure.tourvisio.support;

/**
 * Masking helpers so passenger PII and secrets never reach logs in plain form.
 *
 * <p>The booking client's primary defence is simply to never log request/response
 * <em>bodies</em> (they carry passenger names, email, phone, passport-like fields) nor the
 * {@code Authorization} header. These helpers exist for the rare case a specific value
 * must appear in a diagnostic message.
 */
public final class PiiSanitizer {

    private PiiSanitizer() {
    }

    /** {@code "john.doe@example.com"} → {@code "j***@example.com"}; null/blank → {@code "***"}. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** Keeps only the last 2 chars: {@code "+905551234567"} → {@code "***67"}. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 2) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 2);
    }

    /** Generic masking for any short secret/identifier — never reveals the value. */
    public static String mask(String value) {
        return value == null || value.isBlank() ? "***" : "***";
    }

    /** Masks a bearer token to a fixed placeholder so it is never logged in plain form. */
    public static String maskToken(String token) {
        return "Bearer ***";
    }
}
