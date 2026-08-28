package com.orion.engineering.project.model;

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


    public String getValue()
    {
        return value;
    }


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
