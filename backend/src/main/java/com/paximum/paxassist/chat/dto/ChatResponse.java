package com.paximum.paxassist.chat.dto;

import java.util.List;

public record ChatResponse(
        String reply,
        String sessionId,
        List<Object> hotels,
        List<Object> flights,
        boolean redirectToReservation,
        Object selectedProduct
) {
}
