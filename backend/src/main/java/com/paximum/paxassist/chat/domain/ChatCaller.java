package com.paximum.paxassist.chat.domain;

/**
 * Identifies who a chat turn belongs to, so session ownership can be scoped in the persistence layer
 * without it knowing anything about Spring Security. Exactly one identity is meaningful at a time:
 *
 * <ul>
 *   <li>a logged-in user → {@code userId} ({@code users.id}), {@code guestToken} null;</li>
 *   <li>an anonymous guest → {@code guestToken} (the opaque per-browser key from the
 *       {@code X-Guest-Id} header), {@code userId} null;</li>
 *   <li>neither → {@link #ANONYMOUS}: an ephemeral session that is never persisted to an owner and
 *       cannot be listed or reloaded (defensive fallback; the frontend always sends one identity).</li>
 * </ul>
 *
 * <p>The guest token is a bearer key, not a JWT: it is unguessable but not rotated/expiring, which is
 * acceptable because guest sessions hold only low-value, self-entered search state (never bookings).
 */
public record ChatCaller(Long userId, String guestToken) {

    /** A caller with no identity — used only as a defensive fallback (see class doc). */
    public static final ChatCaller ANONYMOUS = new ChatCaller(null, null);

    public static ChatCaller authenticated(Long userId) {
        return new ChatCaller(userId, null);
    }

    public static ChatCaller guest(String guestToken) {
        return new ChatCaller(null, guestToken);
    }

    /** True when this caller is an anonymous guest identified by an opaque token. */
    public boolean isGuest() {
        return userId == null && guestToken != null;
    }
}
