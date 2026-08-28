package com.orion.engineering.project.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ThingRegistrationMethodConverter implements AttributeConverter<ThingRegistrationMethod, String>
{
    @Override
    public String convertToDatabaseColumn(ThingRegistrationMethod thingRegistrationMethod)
    {
        return thingRegistrationMethod == null ? null : thingRegistrationMethod.getValue();
    }


    @Override
    public ThingRegistrationMethod convertToEntityAttribute(String value)
    {
        return value == null ? null : ThingRegistrationMethod.fromValue(value);
    }
}
