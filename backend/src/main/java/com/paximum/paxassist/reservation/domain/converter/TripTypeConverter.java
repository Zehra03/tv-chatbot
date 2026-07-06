package com.paximum.paxassist.reservation.domain.converter;

import java.util.Locale;

import com.paximum.paxassist.reservation.domain.TripType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link TripType} (uppercase in Java) to/from the snake_case value stored
 * in {@code flight_reservation_details.trip_type} (e.g. {@code ONE_WAY} &lt;-&gt; {@code one_way}).
 */
@Converter(autoApply = true)
public class TripTypeConverter implements AttributeConverter<TripType, String> {

    @Override
    public String convertToDatabaseColumn(TripType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public TripType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TripType.valueOf(dbData.trim().toUpperCase(Locale.ROOT));
    }
}
