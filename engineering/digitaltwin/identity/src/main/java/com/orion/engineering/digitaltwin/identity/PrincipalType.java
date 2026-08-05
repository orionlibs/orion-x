package com.orion.engineering.digitaltwin.identity;

import com.orion.engineering_util.abstraction.OrionEnumeration;

public enum PrincipalType implements OrionEnumeration
{
    Device("Device"),
    Service("Service"),
    Human("Human"),
    Agent("Agent");
    private String name;


    private PrincipalType(String name)
    {
        setName(name);
    }


    public static boolean valueExists(String other)
    {
        PrincipalType[] values = values();

        for(PrincipalType value : values)
        {
            if(value.get().equals(other))
            {
                return true;
            }
        }

        return false;
    }


    public static PrincipalType getEnumForValue(String other)
    {
        PrincipalType[] values = values();

        for(PrincipalType value : values)
        {
            if(value.get().equals(other))
            {
                return value;
            }
        }

        return null;
    }


    @Override
    public String get()
    {
        return getName();
    }


    public String getName()
    {
        return this.name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    @Override
    public boolean is(OrionEnumeration other)
    {
        return other instanceof PrincipalType && this == other;
    }


    @Override
    public boolean isNot(OrionEnumeration other)
    {
        return other instanceof PrincipalType && this != other;
    }
}
