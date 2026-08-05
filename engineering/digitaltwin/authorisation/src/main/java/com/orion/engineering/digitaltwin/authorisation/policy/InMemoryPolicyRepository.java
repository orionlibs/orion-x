package com.orion.engineering.digitaltwin.authorisation.policy;

public class InMemoryPolicyRepository implements PolicyRepository
{
    private final Policy activePolicy;


    public InMemoryPolicyRepository(Policy activePolicy)
    {
        this.activePolicy = activePolicy;
    }


    @Override
    public Policy getActivePolicy()
    {
        return activePolicy;
    }
}
