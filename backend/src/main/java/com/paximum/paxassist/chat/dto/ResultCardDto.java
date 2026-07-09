package com.paximum.paxassist.chat.dto;

/**
 * One inline result card carried on an assistant message, matching the frontend's
 * {@code ResultCard} union ({@code frontend/src/types/chat.ts}): a {@code productType}
 * discriminator plus the raw product payload.
 *
 * @param productType "hotel" | "flight"
 * @param product     the {@code HotelProduct} / {@code FlightProduct} serialized as-is
 */
public record ResultCardDto(String productType, Object product) {
}
