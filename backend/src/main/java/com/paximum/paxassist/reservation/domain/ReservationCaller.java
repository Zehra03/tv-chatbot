package com.paximum.paxassist.reservation.domain;

/**
 * Identifies who a booking belongs to, so ownership can be enforced in the service layer without it
 * knowing anything about Spring Security. Mirrors {@code ChatCaller} in the chat module — booking is
 * open to guests now, and both modules answer the same question ("whose is this?") the same way:
 *
 * <ul>
 *   <li>a logged-in user → {@code userId} ({@code users.id}), {@code guestToken} null;</li>
 *   <li>an anonymous guest → {@code guestToken} (the opaque per-browser key from the
 *       {@code X-Guest-Id} header), {@code userId} null.</li>
 * </ul>
 *
 * <p><b>Why this exists instead of a nullable {@code Long userId}</b>: the ownership checks on the
 * preview → confirm handoff compare the caller against the frozen snapshot. With a bare user id,
 * every guest is {@code null} and {@code null.equals(null)} passes — so any visitor who got hold of
 * a {@code previewId} could confirm (i.e. purchase) another guest's booking. {@link #owns} compares
 * the identity that is actually present, and a caller with no identity at all owns nothing.
 */
public record ReservationCaller(Long userId, String guestToken) {

    public static ReservationCaller authenticated(Long userId) {
        return new ReservationCaller(userId, null);
    }

    public static ReservationCaller guest(String guestToken) {
        return new ReservationCaller(null, guestToken);
    }

    /** True when this caller is an anonymous guest identified by an opaque token. */
    public boolean isGuest() {
        return userId == null && guestToken != null;
    }

    /**
     * True when this caller is the owner recorded on a snapshot/reservation. Deliberately strict:
     * an identity-less caller ({@code null, null}) matches nothing, not even an owner-less record.
     */
    public boolean owns(Long ownerUserId, String ownerGuestToken) {
        if (userId != null) {
            return userId.equals(ownerUserId);
        }
        return guestToken != null && guestToken.equals(ownerGuestToken);
    }
}
