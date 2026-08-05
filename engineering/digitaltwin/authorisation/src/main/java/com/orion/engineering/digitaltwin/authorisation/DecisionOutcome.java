package com.orion.engineering.digitaltwin.authorisation;

import com.orion.engineering_util.abstraction.OrionEnumeration;

public enum DecisionOutcome implements OrionEnumeration
{
    Allow("Allow"),
    Deny("Deny");
    private String name;


    private DecisionOutcome(String name)
    {
        setName(name);
    }


    public static boolean valueExists(String other)
    {
        DecisionOutcome[] values = values();

        for(DecisionOutcome value : values)
        {
            if(value.get().equals(other))
            {
                return true;
            }
        }

        return false;
    }


    public static DecisionOutcome getEnumForValue(String other)
    {
        DecisionOutcome[] values = values();

        for(DecisionOutcome value : values)
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
        return other instanceof DecisionOutcome && this == other;
    }


    @Override
    public boolean isNot(OrionEnumeration other)
    {
        return other instanceof DecisionOutcome && this != other;
    }
}
