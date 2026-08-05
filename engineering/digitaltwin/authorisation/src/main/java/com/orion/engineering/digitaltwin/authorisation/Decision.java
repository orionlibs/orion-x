package com.orion.engineering.digitaltwin.authorisation;

public record Decision(DecisionOutcome outcome, String reason)
{
    public static Decision allow(String reason)
    {
        return new Decision(DecisionOutcome.Allow, reason);
    }


    public static Decision deny(String reason)
    {
        return new Decision(DecisionOutcome.Deny, reason);
    }


    public boolean isAllowed()
    {
        return outcome == DecisionOutcome.Allow;
    }
}
