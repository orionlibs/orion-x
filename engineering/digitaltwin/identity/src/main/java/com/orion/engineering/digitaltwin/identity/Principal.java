package com.orion.engineering.digitaltwin.identity;

import java.util.Set;

public record Principal(String id, PrincipalType type, Set<String> roles)
{
}
