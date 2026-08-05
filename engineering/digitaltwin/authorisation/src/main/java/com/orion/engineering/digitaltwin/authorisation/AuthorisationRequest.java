package com.orion.engineering.digitaltwin.authorisation;

import com.orion.engineering.digitaltwin.identity.Principal;

public record AuthorisationRequest(Principal principal, String action, String resource)
{
}
