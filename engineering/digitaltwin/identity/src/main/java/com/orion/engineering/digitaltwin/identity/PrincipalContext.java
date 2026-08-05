package com.orion.engineering.digitaltwin.identity;

import java.util.Optional;

public final class PrincipalContext
{
    private static final ScopedValue<Principal> CURRENT_PRINCIPAL = ScopedValue.newInstance();


    private PrincipalContext()
    {
    }


    public static void runAs(Principal principal, Runnable action)
    {
        ScopedValue.where(CURRENT_PRINCIPAL, principal)
                   .run(action);
    }


    public static <T, X extends Throwable> T callAs(Principal principal, ScopedValue.CallableOp<T, X> action) throws X
    {
        return ScopedValue.where(CURRENT_PRINCIPAL, principal)
                          .call(action);
    }


    public static Optional<Principal> current()
    {
        return CURRENT_PRINCIPAL.isBound() ? Optional.of(CURRENT_PRINCIPAL.get()) : Optional.empty();
    }
}
