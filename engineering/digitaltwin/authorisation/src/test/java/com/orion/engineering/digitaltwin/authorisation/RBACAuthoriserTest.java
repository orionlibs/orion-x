package com.orion.engineering.digitaltwin.authorisation;

import static org.assertj.core.api.Assertions.assertThat;

import com.orion.engineering.digitaltwin.authorisation.policy.InMemoryPolicyRepository;
import com.orion.engineering.digitaltwin.authorisation.policy.Policy;
import com.orion.engineering.digitaltwin.authorisation.policy.PolicyRule;
import com.orion.engineering.digitaltwin.identity.Principal;
import com.orion.engineering.digitaltwin.identity.PrincipalType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RBACAuthoriserTest
{
    @Test
    @DisplayName("When a policy rule matches the principal's role, action and resource exactly, then authorize allows")
    void test1()
    {
        Policy policy = new Policy("v1", List.of(new PolicyRule("operator", "read", "sensor:temperature")));
        RBACAuthoriser authorizer = new RBACAuthoriser(new InMemoryPolicyRepository(policy));
        Principal principal = new Principal("service-1", PrincipalType.Service, Set.of("operator"));
        Decision decision = authorizer.authorise(new AuthorisationRequest(principal, "read", "sensor:temperature"));
        assertThat(decision.isAllowed()).isTrue();
    }


    @Test
    @DisplayName("When a policy rule has a wildcard action, then authorize allows any action for that role and resource")
    void test2()
    {
        Policy policy = new Policy("v1", List.of(new PolicyRule("operator", "*", "sensor:temperature")));
        RBACAuthoriser authorizer = new RBACAuthoriser(new InMemoryPolicyRepository(policy));
        Principal principal = new Principal("service-1", PrincipalType.Service, Set.of("operator"));
        Decision decision = authorizer.authorise(new AuthorisationRequest(principal, "delete", "sensor:temperature"));
        assertThat(decision.isAllowed()).isTrue();
    }


    @Test
    @DisplayName("When a policy rule has a wildcard resource, then authorize allows that action on any resource for that role")
    void test3()
    {
        Policy policy = new Policy("v1", List.of(new PolicyRule("operator", "read", "*")));
        RBACAuthoriser authorizer = new RBACAuthoriser(new InMemoryPolicyRepository(policy));
        Principal principal = new Principal("service-1", PrincipalType.Service, Set.of("operator"));
        Decision decision = authorizer.authorise(new AuthorisationRequest(principal, "read", "sensor:pressure"));
        assertThat(decision.isAllowed()).isTrue();
    }


    @Test
    @DisplayName("When the principal has no roles, then authorize denies with a reason")
    void test4()
    {
        Policy policy = new Policy("v1", List.of(new PolicyRule("operator", "read", "*")));
        RBACAuthoriser authorizer = new RBACAuthoriser(new InMemoryPolicyRepository(policy));
        Principal principal = new Principal("service-1", PrincipalType.Service, Set.of());
        Decision decision = authorizer.authorise(new AuthorisationRequest(principal, "read", "sensor:pressure"));
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.reason()).contains("no roles assigned");
    }


    @Test
    @DisplayName("When no policy rule matches the principal's roles, then authorize denies by default with a reason")
    void test5()
    {
        Policy policy = new Policy("v1", List.of(new PolicyRule("operator", "read", "sensor:temperature")));
        RBACAuthoriser authorizer = new RBACAuthoriser(new InMemoryPolicyRepository(policy));
        Principal principal = new Principal("service-1", PrincipalType.Service, Set.of("viewer"));
        Decision decision = authorizer.authorise(new AuthorisationRequest(principal, "read", "sensor:temperature"));
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.reason()).contains("default deny");
    }
}
