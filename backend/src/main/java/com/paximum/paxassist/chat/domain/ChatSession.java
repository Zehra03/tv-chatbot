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
    private Map<String, Object> accumulatedCriteria;
    private String flowState;
    private String pendingQuestion;
    private List<Object> lastResultCards;

    public ChatSession(String id) {
        this.id = id;
        this.accumulatedCriteria = new HashMap<>();
        this.flowState = "IDLE";
        this.pendingQuestion = null;
        this.lastResultCards = new ArrayList<>();
    }

    public String getId() { return id; }

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
}
