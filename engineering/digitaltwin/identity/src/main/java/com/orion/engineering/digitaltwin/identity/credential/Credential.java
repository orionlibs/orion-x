package com.orion.engineering.digitaltwin.identity.credential;

import com.orion.engineering.digitaltwin.identity.PrincipalType;
import java.util.Set;

public record Credential(String principalID, PrincipalType type, String sharedSecret, Set<String> roles)
{
}
