package com.orion.engineering.digitaltwin.authorisation;

import com.orion.engineering.digitaltwin.authorisation.policy.Policy;
import com.orion.engineering.digitaltwin.authorisation.policy.PolicyRepository;
import com.orion.engineering.digitaltwin.authorisation.policy.PolicyRule;
import com.orion.engineering.digitaltwin.identity.Principal;

public class RBACAuthoriser
{
    private static final String WILDCARD = "*";
    private final PolicyRepository policyRepository;


    public RBACAuthoriser(PolicyRepository policyRepository)
    {
        this.policyRepository = policyRepository;
    }


    public Decision authorise(AuthorisationRequest request)
    {
        Principal principal = request.principal();

        if(principal.roles().isEmpty())
        {
            return Decision.deny("Principal " + principal.id() + " has no roles assigned");
        }

        Policy policy = policyRepository.getActivePolicy();

        for(PolicyRule rule : policy.rules())
        {
            if(principal.roles().contains(rule.role())
                            && matches(rule.action(), request.action())
                            && matches(rule.resource(), request.resource()))
            {
                return Decision.allow("Rule matched: role=" + rule.role() + " action=" + rule.action() + " resource=" + rule.resource() + " (policy version " + policy.version() + ")");
            }
        }

        return Decision.deny("No policy rule permits roles " + principal.roles() + " to perform " + request.action() + " on " + request.resource() + " (default deny, policy version " + policy.version() + ")");
    }


    private boolean matches(String pattern, String value)
    {
        return pattern.equals(WILDCARD) || pattern.equals(value);
    }
}
