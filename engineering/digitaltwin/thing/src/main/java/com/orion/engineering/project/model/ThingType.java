package com.orion.engineering.project.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ThingType
{
    DEVICE("device"),
    SENSOR("sensor"),
    GATEWAY("gateway"),
    ACTUATOR("actuator"),
    OBJECT("object"),
    BUILDING("building"),
    MACHINE("machine");
    private final String value;


    ThingType(String value)
    {
        this.value = value;
    }


    @JsonValue
    public String getValue()
    {
        return value;
    }


    @JsonCreator
    public static ThingType fromValue(String value)
    {
        for(ThingType thingType : values())
        {
            if(thingType.value.equals(value))
            {
                return thingType;
            }
        }
        throw new IllegalArgumentException("Unknown thing_type: " + value);
    }
}
