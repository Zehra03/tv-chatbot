package com.paximum.paxassist.hotel;

import java.math.BigDecimal;

public record HotelProduct(
    String id,
    String hotelName,
    String region,
    int stars,
    BigDecimal price,
    String currency,
    String boardType,
    boolean availability
) {}
