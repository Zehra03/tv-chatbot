package com.paximum.paxassist.chat.controller;

import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChatRequest;
import com.paximum.paxassist.chat.dto.ChatResponse;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.hotel.HotelSearchCriteria;
import com.paximum.paxassist.hotel.TourVisioHotelApiClient;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionStore sessionStore;
    private final TourVisioHotelApiClient hotelApiClient;

    public ChatController(ChatService chatService, ChatSessionStore sessionStore, TourVisioHotelApiClient hotelApiClient) {
        this.chatService = chatService;
        this.sessionStore = sessionStore;
        this.hotelApiClient = hotelApiClient;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        ChatSession session = sessionStore.getOrCreate(request.sessionId());
        String reply = chatService.chat(request.message());
        
        HotelSearchCriteria criteria = new HotelSearchCriteria("Antalya");
        List<HotelProduct> products = hotelApiClient.searchHotels(criteria);
        
        @SuppressWarnings("unchecked")
        List<Object> hotels = (List<Object>)(List<?>) products;

        return new ChatResponse(reply, session.getId(), hotels, false, null);
    }
}