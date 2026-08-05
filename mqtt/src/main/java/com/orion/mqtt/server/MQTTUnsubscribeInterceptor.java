package com.orion.mqtt.server;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.interceptor.unsubscribe.UnsubscribeInboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.unsubscribe.parameter.UnsubscribeInboundInput;
import com.hivemq.extension.sdk.api.interceptor.unsubscribe.parameter.UnsubscribeInboundOutput;

public class MQTTUnsubscribeInterceptor implements UnsubscribeInboundInterceptor
{
    @Override
    public void onInboundUnsubscribe(@NotNull UnsubscribeInboundInput unsubscribeInboundInput, @NotNull UnsubscribeInboundOutput unsubscribeInboundOutput)
    {
        /*String clientId = unsubscribeInboundInput.getClientInformation().getClientId();
        unsubscribeInboundInput.getUnsubscribePacket()
                        .getTopicFilters()
                        .forEach(topic -> {
                            TopicSubscribersDAO.delete(topic, clientId);
                            DataPacketsDAO.deleteDataPacketsForTopicAndClientId(topic, clientId);
                        });*/
    }
}
