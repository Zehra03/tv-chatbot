package com.paximum.paxassist.reservation.domain.converter;

import java.util.Locale;

import com.paximum.paxassist.reservation.domain.PassengerType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link PassengerType} (uppercase in Java) to/from the lowercase value
 * stored in {@code passengers.passenger_type}.
 */
@Converter(autoApply = true)
public class PassengerTypeConverter implements AttributeConverter<PassengerType, String> {

    @Override
    public String convertToDatabaseColumn(PassengerType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public PassengerType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PassengerType.valueOf(dbData.trim().toUpperCase(Locale.ROOT));
    }
}
