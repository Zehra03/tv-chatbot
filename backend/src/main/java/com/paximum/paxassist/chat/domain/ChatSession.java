package com.paximum.paxassist.chat.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ChatSession {

    private final String id;
    private Long userId;
    private Map<String, Object> accumulatedCriteria;
    private String flowState;
    private String pendingQuestion;
    private List<Object> lastResultCards;
    private List<ChatMessage> messages;

    public ChatSession(String id) {
        this.id = id;
        this.accumulatedCriteria = new HashMap<>();
        this.flowState = "IDLE";
        this.pendingQuestion = null;
        this.lastResultCards = new ArrayList<>();
        this.messages = new ArrayList<>();
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

    public List<ChatMessage> getMessages() { return messages; }

    public void addMessage(ChatMessage message) { this.messages.add(message); }
}
