package com.orion.engineering.digitaltwin.authorisation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryPolicyRepositoryTest
{
    @Test
    @DisplayName("When a policy is provided, then getActivePolicy returns it")
    void test1()
    {
        Policy policy = new Policy("v1", List.of(new PolicyRule("operator", "read", "sensor:temperature")));
        InMemoryPolicyRepository repository = new InMemoryPolicyRepository(policy);
        assertThat(repository.getActivePolicy()).isEqualTo(policy);
    }
}
