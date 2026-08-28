package com.orion.engineering.project.model;

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


    public String getValue()
    {
        return value;
    }


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
