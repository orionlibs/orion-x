package com.orion.engineering.digitaltwin.identity.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.orion.engineering.digitaltwin.identity.PrincipalType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryCredentialStoreTest
{
    @Test
    @DisplayName("When a credential exists for a principal id, then findByPrincipalId returns it")
    void test1()
    {
        Credential credential = new Credential("device-1", PrincipalType.Device, "s3cr3t", Set.of("telemetry:publish"));
        InMemoryCredentialStore store = new InMemoryCredentialStore(List.of(credential));
        assertThat(store.findByPrincipalID("device-1")).contains(credential);
    }


    @Test
    @DisplayName("When no credential exists for a principal id, then findByPrincipalId returns empty")
    void test2()
    {
        InMemoryCredentialStore store = new InMemoryCredentialStore(List.of());
        assertThat(store.findByPrincipalID("unknown")).isEmpty();
    }
}
