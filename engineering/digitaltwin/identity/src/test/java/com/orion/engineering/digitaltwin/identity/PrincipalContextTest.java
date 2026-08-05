package com.orion.engineering.digitaltwin.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrincipalContextTest
{
    @Test
    @DisplayName("When no principal has been bound, then current returns empty")
    void test1()
    {
        assertThat(PrincipalContext.current()).isEmpty();
    }


    @Test
    @DisplayName("When runAs binds a principal, then current returns that principal inside the scope")
    void test2()
    {
        Principal principal = new Principal("device-1", PrincipalType.Device, Set.of("telemetry:publish"));
        PrincipalContext.runAs(principal, () -> assertThat(PrincipalContext.current()).contains(principal));
    }


    @Test
    @DisplayName("When runAs completes, then current returns empty outside the scope")
    void test3()
    {
        Principal principal = new Principal("device-1", PrincipalType.Device, Set.of("telemetry:publish"));
        PrincipalContext.runAs(principal, () -> {});
        assertThat(PrincipalContext.current()).isEmpty();
    }


    @Test
    @DisplayName("When callAs binds a principal, then current returns that principal inside the call and the return value passes through")
    void test4() throws Exception
    {
        Principal principal = new Principal("service-1", PrincipalType.Service, Set.of());
        String result = PrincipalContext.callAs(principal, () -> PrincipalContext.current().orElseThrow().id());
        assertThat(result).isEqualTo("service-1");
    }
}
