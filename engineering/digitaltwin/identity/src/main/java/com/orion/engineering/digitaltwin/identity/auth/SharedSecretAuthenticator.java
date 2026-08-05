package com.orion.engineering.digitaltwin.identity.auth;

import com.orion.engineering.digitaltwin.identity.Principal;
import com.orion.engineering.digitaltwin.identity.credential.Credential;
import com.orion.engineering.digitaltwin.identity.credential.CredentialStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class SharedSecretAuthenticator
{
    private final CredentialStore credentialStore;


    public SharedSecretAuthenticator(CredentialStore credentialStore)
    {
        this.credentialStore = credentialStore;
    }


    public Principal authenticate(String principalId, String presentedSecret)
    {
        Credential credential = credentialStore.findByPrincipalID(principalId)
                                               .orElseThrow(() -> new AuthenticationException("Unknown principal: " + principalId));

        if(!secretsMatch(credential.sharedSecret(), presentedSecret))
        {
            throw new AuthenticationException("Invalid credentials for principal: " + principalId);
        }

        return new Principal(credential.principalID(), credential.type(), credential.roles());
    }


    private boolean secretsMatch(String expected, String presented)
    {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
