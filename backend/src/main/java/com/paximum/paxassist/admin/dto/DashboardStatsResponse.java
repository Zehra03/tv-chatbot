package com.paximum.paxassist.admin.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DashboardStatsResponse(
        long totalReservations,
        long activeUsers,
        Map<String, BigDecimal> totalRevenueByCurrency
) {}
