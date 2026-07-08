package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.hotel.HotelProduct;

/**
 * Small package-private helper that reads comparable attributes off a heterogeneous result card
 * ({@link HotelProduct} record or {@link FlightProduct}). Shared by the FILTER and SELECT
 * handlers so the type-narrowing lives in exactly one place (DRY).
 */
final class ProductCards {

    private ProductCards() {
    }

    static BigDecimal priceOf(Object card) {
        if (card instanceof HotelProduct hotel) {
            return hotel.price();
        }
        if (card instanceof FlightProduct flight) {
            return flight.getPrice();
        }
        return null;
    }

    static Integer starsOf(Object card) {
        if (card instanceof HotelProduct hotel) {
            return hotel.stars();
        }
        return null; // flights have no star rating
    }
}
