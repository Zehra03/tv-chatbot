package com.paximum.paxassist.chat.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory session state. Swapped for a JPA entity once the DB teammate
 * delivers the chat_sessions entity + repository (Zehra koordinasyonu).
 */
public class ChatSession {

    private final String id;
    // Owner (users.id). Sessions are scoped to the authenticated user so listing/loading/deleting
    // never crosses users. Null only for legacy/in-memory sessions with no principal.
    private Long userId;
    private Map<String, Object> accumulatedCriteria;
    private String flowState;
    private String pendingQuestion;
    private List<Object> lastResultCards;
    private final List<ChatMessage> messages = new ArrayList<>();
    // "HOTEL" | "FLIGHT" | null — the domain of the last search, so FILTER/SELECT know
    // which result list they are acting on. Kept as a String so this domain type does not
    // depend on the ai module's IntentType enum.
    private String activeDomain;

    public ChatSession(String id) {
        this.id = id;
        this.accumulatedCriteria = new HashMap<>();
        this.flowState = "IDLE";
        this.pendingQuestion = null;
        this.lastResultCards = new ArrayList<>();
    }

    public String getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Map<String, Object> getAccumulatedCriteria() { return accumulatedCriteria; }
    public void setAccumulatedCriteria(Map<String, Object> accumulatedCriteria) {
        this.accumulatedCriteria = accumulatedCriteria;
    }

    public String getFlowState() { return flowState; }
    public void setFlowState(String flowState) { this.flowState = flowState; }

    public String getPendingQuestion() { return pendingQuestion; }
    public void setPendingQuestion(String pendingQuestion) { this.pendingQuestion = pendingQuestion; }

    public List<Object> getLastResultCards() { return lastResultCards; }
    public void setLastResultCards(List<Object> lastResultCards) { this.lastResultCards = lastResultCards; }

    /** Append-only transcript; feeds conversation history to intent extraction and aligns
     * with the future chat_messages table. */
    public List<ChatMessage> getMessages() { return messages; }
    public void addMessage(String role, String content) { messages.add(new ChatMessage(role, content)); }

    public String getActiveDomain() { return activeDomain; }
    public void setActiveDomain(String activeDomain) { this.activeDomain = activeDomain; }
}
