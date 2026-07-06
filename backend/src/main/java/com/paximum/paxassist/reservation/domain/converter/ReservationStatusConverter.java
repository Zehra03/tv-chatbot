package com.paximum.paxassist.reservation.domain.converter;

import java.util.Locale;

import com.paximum.paxassist.reservation.domain.ReservationStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link ReservationStatus} (uppercase in Java) to/from the lowercase
 * value stored in {@code reservations.status}. {@code autoApply = true} so every
 * {@code ReservationStatus} field is converted without a per-field {@code @Convert}.
 */
@Converter(autoApply = true)
public class ReservationStatusConverter implements AttributeConverter<ReservationStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReservationStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ReservationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReservationStatus.valueOf(dbData.trim().toUpperCase(Locale.ROOT));
    }
}
