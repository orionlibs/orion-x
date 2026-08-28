package com.orion.engineering.project.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ThingTypeConverter implements AttributeConverter<ThingType, String>
{
    @Override
    public String convertToDatabaseColumn(ThingType thingType)
    {
        return thingType == null ? null : thingType.getValue();
    }


    @Override
    public ThingType convertToEntityAttribute(String value)
    {
        return value == null ? null : ThingType.fromValue(value);
    }
}
