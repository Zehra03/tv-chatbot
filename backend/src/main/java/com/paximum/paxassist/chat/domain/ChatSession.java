package com.paximum.paxassist.chat.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory session state. Swapped for a JPA entity once the DB teammate
 * delivers the chat_sessions entity + repository (Zehra koordinasyonu).
 * Fields align to DB columns: accumulated_criteria (chat_sessions),
 * last_result_cards (chat_messages.result_cards), active_domain (chat_sessions).
 *
 * <p>Result cards are partitioned per domain rather than held in one shared list: a flight search
 * must not overwrite the hotel cards the user may return to ("otele dönelim"). A domain switch
 * therefore never deletes anything — the other domain's cards simply go passive until that domain
 * is active again.
 */
public class ChatSession {

    /**
     * Box key for cards recorded while no domain is known — legacy rows written before
     * active_domain existed, and sessions restored without it. Keeps the pre-partition behaviour
     * (one shared list) intact for those instead of silently dropping their cards.
     */
    private static final String UNSCOPED_DOMAIN = "";

    private final String id;
    // Owner (users.id). Sessions are scoped to the authenticated user so listing/loading/deleting
    // never crosses users. Null for guest-owned (see guestToken) or legacy/in-memory sessions.
    private Long userId;
    // Opaque per-visitor key for guest-owned sessions (X-Guest-Id). Mutually exclusive with userId:
    // set only when the owner is an anonymous guest. Null for user-owned/legacy sessions.
    private String guestToken;
    private Map<String, Object> accumulatedCriteria;
    // Result cards keyed by domain ("HOTEL" | "FLIGHT" | UNSCOPED_DOMAIN). Reads/writes go through
    // activeDomain, so each domain accumulates its own list and neither can clobber the other.
    private final Map<String, List<Object>> resultCardsByDomain = new HashMap<>();
    private final List<ChatMessage> messages = new ArrayList<>();
    // "HOTEL" | "FLIGHT" | null — the domain currently being worked on. It selects which result-card
    // box FILTER/SELECT act on, so it is also the switch that makes the other domain's cards
    // passive. Kept as a String so this domain type does not depend on the ai module's IntentType enum.
    private String activeDomain;

    public ChatSession(String id) {
        this.id = id;
        this.accumulatedCriteria = new HashMap<>();
    }

    public String getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getGuestToken() { return guestToken; }
    public void setGuestToken(String guestToken) { this.guestToken = guestToken; }

    public Map<String, Object> getAccumulatedCriteria() { return accumulatedCriteria; }
    public void setAccumulatedCriteria(Map<String, Object> accumulatedCriteria) {
        this.accumulatedCriteria = accumulatedCriteria;
    }

    /**
     * The ACTIVE domain's cards — what FILTER/SELECT operate on. Never null: a domain with no search
     * yet yields an empty list, so callers keep their existing "empty means nothing to filter" check.
     */
    public List<Object> getLastResultCards() { return getResultCards(activeDomain); }

    /** Records cards for the ACTIVE domain, leaving every other domain's cards untouched. */
    public void setLastResultCards(List<Object> lastResultCards) {
        setResultCards(activeDomain, lastResultCards);
    }

    /** Cards of a specific domain regardless of which one is active. Never null. */
    public List<Object> getResultCards(String domain) {
        List<Object> cards = resultCardsByDomain.get(boxKey(domain));
        return (cards != null) ? cards : List.of();
    }

    /** Replaces a specific domain's cards, leaving every other domain's cards untouched. */
    public void setResultCards(String domain, List<Object> cards) {
        resultCardsByDomain.put(boxKey(domain),
                (cards != null) ? new ArrayList<>(cards) : new ArrayList<>());
    }

    /** Append-only transcript; feeds conversation history to intent extraction and aligns
     * with the future chat_messages table. */
    public List<ChatMessage> getMessages() { return messages; }
    public void addMessage(String role, String content) { messages.add(new ChatMessage(role, content)); }

    public String getActiveDomain() { return activeDomain; }
    public void setActiveDomain(String activeDomain) { this.activeDomain = activeDomain; }

    private static String boxKey(String domain) {
        return (domain != null) ? domain : UNSCOPED_DOMAIN;
    }
}
