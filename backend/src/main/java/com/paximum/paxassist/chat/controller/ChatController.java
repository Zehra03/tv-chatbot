package com.paximum.paxassist.chat.controller;

import com.paximum.paxassist.chat.domain.ChatMessage;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChatHistoryResponse;
import com.paximum.paxassist.chat.dto.ChatRequest;
import com.paximum.paxassist.chat.dto.ChatResponse;
import com.paximum.paxassist.chat.dto.ErrorResponse;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import com.paximum.paxassist.common.exception.ResourceNotFoundException;
import com.paximum.paxassist.common.exception.UnauthorizedAccessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "Chat", description = "Chatbot mesajlaşma işlemleri")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionStore sessionStore;

    public ChatController(ChatService chatService, ChatSessionStore sessionStore) {
        this.chatService = chatService;
        this.sessionStore = sessionStore;
    }

    @Operation(summary = "Chatbot'a mesaj gönder",
               description = "Kullanıcı mesajını chatbot'a iletir, bot cevabı döner. " +
                             "sessionId null ise yeni oturum oluşturulur.")
    @ApiResponse(responseCode = "200", description = "Bot cevabı",
            content = @Content(schema = @Schema(implementation = ChatResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validasyon hatası",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI servisi geçici olarak ulaşılamıyor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        Long userId = extractUserId();

        ChatSession session = sessionStore.getOrCreate(request.sessionId());

        if (session.getUserId() != null && userId != null
                && !session.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Bu oturuma erişim yetkiniz yok");
        }
        if (session.getUserId() == null && userId != null) {
            session.setUserId(userId);
        }

        String reply = chatService.chat(request.message()).reply();
        session.addMessage(ChatMessage.user(request.message()));
        session.addMessage(ChatMessage.assistant(reply));
        sessionStore.save(session);

        return new ChatResponse(reply, session.getId(), null, null, false, null);
    }

    @Operation(summary = "Sohbet geçmişini getir",
               description = "Belirtilen oturuma ait tüm mesajları created_at sırasıyla döner.")
    @ApiResponse(responseCode = "200", description = "Sohbet geçmişi",
            content = @Content(schema = @Schema(implementation = ChatHistoryResponse.class)))
    @ApiResponse(responseCode = "403", description = "Bu oturuma erişim yetkiniz yok",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Oturum bulunamadı",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{sessionId}")
    public ChatHistoryResponse history(@PathVariable String sessionId) {
        Long userId = extractUserId();

        ChatSession session = sessionStore.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sohbet oturumu bulunamadı"));

        if (session.getUserId() != null && userId != null
                && !session.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Bu oturuma erişim yetkiniz yok");
        }

        return new ChatHistoryResponse(session.getId(), session.getMessages());
    }

    @Operation(summary = "Sohbet geçmişini sil",
               description = "Belirtilen oturumu ve tüm mesaj geçmişini siler.")
    @ApiResponse(responseCode = "204", description = "Sohbet silindi")
    @ApiResponse(responseCode = "403", description = "Bu oturuma erişim yetkiniz yok",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Oturum bulunamadı",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId) {
        Long userId = extractUserId();

        ChatSession session = sessionStore.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sohbet oturumu bulunamadı"));

        if (session.getUserId() != null && userId != null
                && !session.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Bu oturuma erişim yetkiniz yok");
        }

        // CAS remove: chat_messages deleted via ON DELETE CASCADE (V1__initial_schema.sql)
        sessionStore.deleteById(sessionId, session);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Streaming chat (SSE)",
               description = "Chatbot cevabını Server-Sent Events akışı olarak döner.")
    @ApiResponse(responseCode = "200", description = "SSE akışı")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody @Valid ChatRequest request) {
        sessionStore.getOrCreate(request.sessionId());
        return chatService.streamChat(request.message());
    }

    private Long extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
