package com.orion.engineering.digitaltwin.identity.credential;

import java.util.Optional;

public interface CredentialStore
{
    Optional<Credential> findByPrincipalID(String principalID);
}
