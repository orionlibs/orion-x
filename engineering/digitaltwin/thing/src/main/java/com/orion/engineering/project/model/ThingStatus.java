package com.orion.engineering.project.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ThingStatus
{
    ACTIVE("active"),
    INACTIVE("inactive"),
    OFFLINE("offline"),
    DECOMMISSIONED("decommissioned");
    private final String value;


    ThingStatus(String value)
    {
        this.value = value;
    }


    @JsonValue
    public String getValue()
    {
        return value;
    }


    @JsonCreator
    public static ThingStatus fromValue(String value)
    {
        for(ThingStatus thingStatus : values())
        {
            if(thingStatus.value.equals(value))
            {
                return thingStatus;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
