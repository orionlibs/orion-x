package com.orion.mqtt.server;

import com.hivemq.extension.sdk.api.auth.Authenticator;
import com.hivemq.extension.sdk.api.auth.SimpleAuthenticator;
import com.hivemq.extension.sdk.api.auth.parameter.AuthenticatorProviderInput;
import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthInput;
import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthOutput;
import com.hivemq.extension.sdk.api.packets.connect.ConnackReasonCode;
import com.hivemq.extension.sdk.api.services.auth.provider.AuthenticatorProvider;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class MQTTAuthenticatorProvider implements AuthenticatorProvider
{
    private final static Logger log;
    private static int counter;

    static
    {
        log = Logger.getLogger(MQTTAuthenticatorProvider.class.getName());
    }

    public static void addLogHandler(Handler handler)
    {
        log.addHandler(handler);
    }


    public static void removeLogHandler(Handler handler)
    {
        log.removeHandler(handler);
    }


    @Override
    public Authenticator getAuthenticator(AuthenticatorProviderInput authenticatorProviderInput)
    {
        return new SimpleAuthenticator()
        {
            @Override
            public void onConnect(SimpleAuthInput input, SimpleAuthOutput output)
            {
                Optional<String> username = input.getConnectPacket().getUserName();
                Optional<ByteBuffer> password = input.getConnectPacket().getPassword();
                if(!username.isPresent() || !password.isPresent())
                {
                    log.severe("NOT_AUTHORIZED_" + counter);
                    counter++;
                    output.failAuthentication(ConnackReasonCode.NOT_AUTHORIZED);
                }
                ByteBuffer buffer = password.get();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                String passwordString = new String(bytes);
                if(isValidCredentials(username.get(), passwordString))
                {
                    output.authenticateSuccessfully();
                }
                else
                {
                    log.severe("NOT_AUTHORIZED_" + counter);
                    counter++;
                    output.failAuthentication(ConnackReasonCode.NOT_AUTHORIZED);
                }
            }
        };
    }


    private boolean isValidCredentials(String username, String password)
    {
        //replace this with your actual authentication logic
        //for example, check against a database, LDAP, or other authentication service
        return "admin".equals(username) && "password".equals(password);
    }
}
