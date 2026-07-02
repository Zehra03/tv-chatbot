package com.paximum.paxassist.reservation;

import java.math.BigDecimal;
import java.util.List;

public record ReservationPreviewResponse(
        BigDecimal totalAmount,
        String currency,
        String productType,
        String selectedProductId,
        List<String> breakdown
) {}
