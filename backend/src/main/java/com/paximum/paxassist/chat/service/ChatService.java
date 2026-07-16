package com.paximum.paxassist.chat.service;

import com.paximum.paxassist.chat.config.PaxiSystemPrompt;
import com.paximum.paxassist.chat.dto.AiReply;
import com.paximum.paxassist.chat.exception.AiClientException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_DELAY_MS = 2000;

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public AiReply chat(@NonNull String message) {
        return chat(message, null);
    }

    /**
     * @param firstName the caller's first name so Paxi can greet them by it, or null for a guest
     */
    public AiReply chat(@NonNull String message, @Nullable String firstName) {
        String systemPrompt = PaxiSystemPrompt.forUser(firstName);
        AiClientException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                AiReply reply = chatClient.prompt()
                        .system(systemPrompt)
                        .user(message)
                        .call()
                        .entity(AiReply.class);
                if (reply == null) {
                    throw new AiClientException(AiClientException.Code.UNKNOWN, "AI yanıt üretemedi");
                }
                return reply;
            } catch (AiClientException e) {
                if (isRetryable(e) && attempt < MAX_ATTEMPTS) {
                    lastError = e;
                    sleep(BASE_DELAY_MS * attempt);
                } else {
                    throw e;
                }
            } catch (RuntimeException e) {
                if (isParseFailure(e)) {
                    return new AiReply("Bu konuda yardımcı olamıyorum. Otel veya uçuş araması için buradayım.");
                }
                AiClientException classified = classify(e);
                if (isRetryable(classified) && attempt < MAX_ATTEMPTS) {
                    lastError = classified;
                    sleep(BASE_DELAY_MS * attempt);
                } else {
                    throw classified;
                }
            }
        }
        throw (lastError != null) ? lastError
                : new AiClientException(AiClientException.Code.UNKNOWN, "AI servisinden yanıt alınamadı");
    }

    private boolean isParseFailure(RuntimeException e) {
        String msg = fullMessage(e);
        return msg.contains("jsonprocessing") || msg.contains("cannot deserialize")
                || msg.contains("unrecognized field") || msg.contains("unexpected character")
                || msg.contains("no content to map") || msg.contains("invalid json")
                || msg.contains("converter") || msg.contains("outputconverter");
    }

    private boolean isRetryable(AiClientException e) {
        return e.getCode() == AiClientException.Code.UNAVAILABLE
                || e.getCode() == AiClientException.Code.TIMEOUT
                || e.getCode() == AiClientException.Code.RATE_LIMITED;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private AiClientException classify(RuntimeException e) {
        String msg = fullMessage(e);
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("too many requests")) {
            return new AiClientException(AiClientException.Code.RATE_LIMITED,
                    "İstek limiti aşıldı, lütfen bekleyin", e);
        }
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("read timed out")) {
            return new AiClientException(AiClientException.Code.TIMEOUT,
                    "AI servisi yanıt vermedi, lütfen tekrar deneyin", e);
        }
        if (msg.contains("connect") || msg.contains("refused") || msg.contains("unreachable")
                || hasCause(e, java.net.ConnectException.class)) {
            return new AiClientException(AiClientException.Code.UNAVAILABLE,
                    "AI servisi şu an ulaşılamıyor", e);
        }
        if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("server error")) {
            return new AiClientException(AiClientException.Code.UNAVAILABLE,
                    "AI sunucusu geçici hata döndürdü, lütfen tekrar deneyin", e);
        }
        return new AiClientException(AiClientException.Code.UNKNOWN,
                "Beklenmeyen hata, AI servisinden yanıt alınamadı", e);
    }

    private String fullMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        while (t != null) {
            if (t.getMessage() != null) sb.append(t.getMessage().toLowerCase()).append(' ');
            t = t.getCause();
        }
        return sb.toString();
    }

    private boolean hasCause(Throwable e, Class<?> type) {
        Throwable t = e.getCause();
        while (t != null) {
            if (type.isInstance(t)) return true;
            t = t.getCause();
        }
        return false;
    }
}
