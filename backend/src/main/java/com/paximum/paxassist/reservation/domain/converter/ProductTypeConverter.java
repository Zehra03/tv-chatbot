package com.paximum.paxassist.reservation.domain.converter;

import java.util.Locale;

import com.paximum.paxassist.reservation.domain.ProductType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link ProductType} (uppercase in Java) to/from the lowercase value
 * stored in {@code reservations.product_type}.
 */
@Converter(autoApply = true)
public class ProductTypeConverter implements AttributeConverter<ProductType, String> {

    @Override
    public String convertToDatabaseColumn(ProductType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ProductType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProductType.valueOf(dbData.trim().toUpperCase(Locale.ROOT));
    }
}
