package com.orion.engineering.digitaltwin.authorisation.policy;

import java.util.List;

public record Policy(String version, List<PolicyRule> rules)
{
}
