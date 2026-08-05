package com.orion.mqtt.server;

import com.hivemq.extension.sdk.api.auth.Authorizer;
import com.hivemq.extension.sdk.api.auth.PublishAuthorizer;
import com.hivemq.extension.sdk.api.auth.SubscriptionAuthorizer;
import com.hivemq.extension.sdk.api.auth.parameter.AuthorizerProviderInput;
import com.hivemq.extension.sdk.api.auth.parameter.PublishAuthorizerInput;
import com.hivemq.extension.sdk.api.auth.parameter.PublishAuthorizerOutput;
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerInput;
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerOutput;
import com.hivemq.extension.sdk.api.packets.disconnect.DisconnectReasonCode;
import com.hivemq.extension.sdk.api.packets.general.UserProperties;
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket;
import com.hivemq.extension.sdk.api.services.auth.provider.AuthorizerProvider;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class MQTTAuthorizationProvider implements AuthorizerProvider, PublishAuthorizer, SubscriptionAuthorizer
{
    private final static Logger log;

    static
    {
        log = Logger.getLogger(MQTTAuthorizationProvider.class.getName());
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
    public Authorizer getAuthorizer(AuthorizerProviderInput authorizerProviderInput)
    {
        return this;
    }


    @Override
    public void authorizePublish(PublishAuthorizerInput publishAuthorizerInput, PublishAuthorizerOutput publishAuthorizerOutput)
    {
        PublishPacket publishPacket = publishAuthorizerInput.getPublishPacket();
        if(publishPacket.getTopic().startsWith("forbidden"))
        {
            log.severe("forbidden");
            publishAuthorizerOutput.failAuthorization();
            return;
        }
        UserProperties userProperties = publishPacket.getUserProperties();
        if(userProperties.getFirst("forbidden").isPresent())
        {
            log.severe("forbidden");
            publishAuthorizerOutput.disconnectClient(DisconnectReasonCode.ADMINISTRATIVE_ACTION, "User property not allowed");
            return;
        }
        publishAuthorizerOutput.authorizeSuccessfully();
    }


    @Override
    public void authorizeSubscribe(SubscriptionAuthorizerInput subscriptionAuthorizerInput, SubscriptionAuthorizerOutput subscriptionAuthorizerOutput)
    {
        if(subscriptionAuthorizerInput.getSubscription().getTopicFilter().startsWith("forbidden"))
        {
            log.severe("forbidden");
            subscriptionAuthorizerOutput.failAuthorization();
            return;
        }
        final UserProperties userProperties = subscriptionAuthorizerInput.getUserProperties();
        if(userProperties.getFirst("forbidden").isPresent())
        {
            log.severe("forbidden");
            subscriptionAuthorizerOutput.disconnectClient(DisconnectReasonCode.ADMINISTRATIVE_ACTION, "User property not allowed");
            return;
        }
        subscriptionAuthorizerOutput.authorizeSuccessfully();
    }
}
