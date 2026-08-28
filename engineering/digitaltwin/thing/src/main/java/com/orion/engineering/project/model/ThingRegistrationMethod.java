package com.orion.engineering.project.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ThingRegistrationMethod
{
    MANUAL("manual"),
    MQTT_AUTO("mqtt_auto");
    private final String value;


    ThingRegistrationMethod(String value)
    {
        this.value = value;
    }


    @JsonValue
    public String getValue()
    {
        return value;
    }


    @JsonCreator
    public static ThingRegistrationMethod fromValue(String value)
    {
        for(ThingRegistrationMethod thingRegistrationMethod : values())
        {
            if(thingRegistrationMethod.value.equals(value))
            {
                return thingRegistrationMethod;
            }
        }
        throw new IllegalArgumentException("Unknown registration method: " + value);
    }
}
