package com.paximum.paxassist.chat.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory session state. Swapped for a JPA entity once the DB teammate
 * delivers the chat_sessions entity + repository (Zehra koordinasyonu).
 * Fields align to DB columns: accumulated_criteria (chat_sessions),
 * last_result_cards (chat_messages.result_cards).
 */
public class ChatSession {

    private final String id;
    // Owner (users.id). Sessions are scoped to the authenticated user so listing/loading/deleting
    // never crosses users. Null for guest-owned (see guestToken) or legacy/in-memory sessions.
    private Long userId;
    // Opaque per-visitor key for guest-owned sessions (X-Guest-Id). Mutually exclusive with userId:
    // set only when the owner is an anonymous guest. Null for user-owned/legacy sessions.
    private String guestToken;
    private Map<String, Object> accumulatedCriteria;
    private List<Object> lastApiResultCards; // Raw API results, before FILTER intent
    private List<Object> lastResultCards;    // Results shown to the user (after FILTER)
    // Every outbound+return combination the last round-trip search allows. The shown cards are only
    // one per outbound (the cheapest way to fly it), so these are what the return alternatives for a
    // chosen outbound are drawn from. Empty for hotels and one-way flights.
    private List<Object> roundTripOptions = new ArrayList<>();
    // Set once the user has picked an outbound and is choosing the return; null at every other time.
    private String pendingOutboundLegId;
    // The original facility question ("havuz var mı") the assistant is still waiting on a hotel for,
    // after it asked "hangi otel?". While set, the next turn's answer ("1", "ilk", a name) is routed
    // back to the facility Q&A with THIS question rather than being mistaken for a booking (SELECT).
    // Null whenever no facility clarification is pending.
    private String pendingFacilityQuestion;
    private final List<ChatMessage> messages = new ArrayList<>();
    // "HOTEL" | "FLIGHT" | null — the domain of the last search, so FILTER/SELECT know
    // which result list they are acting on. Kept as a String so this domain type does not
    // depend on the ai module's IntentType enum.
    private String activeDomain;

    public ChatSession(String id) {
        this.id = id;
        this.accumulatedCriteria = new HashMap<>();
        this.lastApiResultCards = new ArrayList<>();
        this.lastResultCards = new ArrayList<>();
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

    public List<Object> getLastApiResultCards() { return lastApiResultCards; }
    public void setLastApiResultCards(List<Object> lastApiResultCards) { this.lastApiResultCards = lastApiResultCards; }

    public List<Object> getLastResultCards() { return lastResultCards; }
    public void setLastResultCards(List<Object> lastResultCards) { this.lastResultCards = lastResultCards; }

    /** Append-only transcript; feeds conversation history to intent extraction and aligns
     * with the future chat_messages table. */
    public List<ChatMessage> getMessages() { return messages; }
    public void addMessage(String role, String content) { messages.add(new ChatMessage(role, content)); }

    public String getActiveDomain() { return activeDomain; }
    public void setActiveDomain(String activeDomain) { this.activeDomain = activeDomain; }

    public List<Object> getRoundTripOptions() { return roundTripOptions; }
    public void setRoundTripOptions(List<Object> roundTripOptions) { this.roundTripOptions = roundTripOptions; }

    public String getPendingOutboundLegId() { return pendingOutboundLegId; }
    public void setPendingOutboundLegId(String pendingOutboundLegId) {
        this.pendingOutboundLegId = pendingOutboundLegId;
    }

    public String getPendingFacilityQuestion() { return pendingFacilityQuestion; }
    public void setPendingFacilityQuestion(String pendingFacilityQuestion) {
        this.pendingFacilityQuestion = pendingFacilityQuestion;
    }

    /**
     * Criteria that describe the traveller rather than the search, so they stay true after the user
     * switches from hotels to flights ("vazgeçtim, uçak arıyorum") and must not be asked again.
     * Keys mirror {@code SlotCriteria}'s field names, which is the shape stored in
     * {@code accumulatedCriteria}.
     */
    private static final Set<String> TRAVELLER_CRITERIA_KEYS =
            Set.of("adults", "children", "childAges", "nationality", "currency");

    /**
     * Switches the conversation to another product domain: the previous domain's search criteria and
     * its result cards are dropped, while the traveller's own details are carried over.
     *
     * <p>Dropping the cards is what stops a stale hotel list from being filtered or selected while
     * the user is already talking about flights — the new domain has no results until its own search
     * succeeds.
     */
    public void switchDomain(String domain) {
        if (accumulatedCriteria != null) {
            accumulatedCriteria.keySet().retainAll(TRAVELLER_CRITERIA_KEYS);
        }
        lastApiResultCards = new ArrayList<>();
        lastResultCards = new ArrayList<>();
        roundTripOptions = new ArrayList<>();
        pendingOutboundLegId = null;
        pendingFacilityQuestion = null;
        activeDomain = domain;
    }
}
