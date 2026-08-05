package com.orion.engineering.digitaltwin.identity.credential;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InMemoryCredentialStore implements CredentialStore
{
    private final Map<String, Credential> credentialsByPrincipalID;


    public InMemoryCredentialStore(Collection<Credential> credentials)
    {
        this.credentialsByPrincipalID = credentials.stream()
                                                   .collect(Collectors.toMap(Credential::principalID, Function.identity()));
    }


    @Override
    public Optional<Credential> findByPrincipalID(String principalID)
    {
        return Optional.ofNullable(credentialsByPrincipalID.get(principalID));
    }
}
