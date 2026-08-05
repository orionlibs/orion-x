package com.orion.engineering.digitaltwin.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orion.engineering.digitaltwin.identity.Principal;
import com.orion.engineering.digitaltwin.identity.PrincipalType;
import com.orion.engineering.digitaltwin.identity.credential.Credential;
import com.orion.engineering.digitaltwin.identity.credential.InMemoryCredentialStore;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SharedSecretAuthenticatorTest
{
    private SharedSecretAuthenticator authenticator;


    @BeforeEach
    void setUp()
    {
        Credential credential = new Credential("device-1", PrincipalType.Device, "s3cr3t", Set.of("telemetry:publish"));
        authenticator = new SharedSecretAuthenticator(new InMemoryCredentialStore(List.of(credential)));
    }


    @Test
    @DisplayName("When the presented secret matches the stored secret, then authenticate returns the principal")
    void test1()
    {
        Principal principal = authenticator.authenticate("device-1", "s3cr3t");
        assertThat(principal).isEqualTo(new Principal("device-1", PrincipalType.Device, Set.of("telemetry:publish")));
    }


    @Test
    @DisplayName("When the principal id is unknown, then authenticate throws AuthenticationException")
    void test2()
    {
        assertThatThrownBy(() -> authenticator.authenticate("unknown", "s3cr3t")).isInstanceOf(AuthenticationException.class);
    }


    @Test
    @DisplayName("When the presented secret does not match, then authenticate throws AuthenticationException")
    void test3()
    {
        assertThatThrownBy(() -> authenticator.authenticate("device-1", "wrong")).isInstanceOf(AuthenticationException.class);
    }
}
