package com.orion.engineering.project.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ThingStatusConverter implements AttributeConverter<ThingStatus, String>
{
    @Override
    public String convertToDatabaseColumn(ThingStatus thingStatus)
    {
        return thingStatus == null ? null : thingStatus.getValue();
    }


    @Override
    public ThingStatus convertToEntityAttribute(String value)
    {
        return value == null ? null : ThingStatus.fromValue(value);
    }
}
