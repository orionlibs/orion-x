package com.orion.mqtt.server;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.interceptor.publish.PublishInboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;

public class MQTTPublishInterceptor implements PublishInboundInterceptor
{
    @Override
    public void onInboundPublish(@NotNull PublishInboundInput publishInboundInput, @NotNull PublishInboundOutput publishInboundOutput)
    {
        /*String publisherId = publishInboundInput.getClientInformation().getClientId();
        String topic = publishInboundInput.getPublishPacket().getTopic();
        if(publishInboundOutput.getPublishPacket().getQos() == Qos.AT_LEAST_ONCE
                        || publishInboundOutput.getPublishPacket().getQos() == Qos.EXACTLY_ONCE)
        {
            SQLTimestamp payloadPyblicationDateTime = SQLTimestamp.of(publishInboundOutput.getPublishPacket().getTimestamp());
            int qualityOfServiceLevel = publishInboundOutput.getPublishPacket().getQos().getQosNumber();
            if(publishInboundOutput.getPublishPacket().getPayload().isPresent())
            {
                ByteBuffer buffer = publishInboundOutput.getPublishPacket().getPayload().get();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                String payload = new String(bytes);
                IterationCallback<SubscriberWithFilterResult> subscribersForTopic = new IterationCallback()
                {
                    @Override
                    public void iterate(IterationContext iterationContext, Object o)
                    {
                        SubscriberWithFilterResult result = (SubscriberWithFilterResult)o;
                        storePayloadToDatabase(publisherId, result.getClientId(), topic, payload, qualityOfServiceLevel, payloadPyblicationDateTime);
                    }
                };
                CompletableFuture<Void> searchResults = Services.subscriptionStore().iterateAllSubscribersWithTopicFilter(topic, subscribersForTopic);
                try
                {
                    searchResults.get();
                }
                catch(InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
                catch(ExecutionException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }*/
        // Optional: Modify the publish message
        // publishInboundOutput.getPublishPacket().setPayload(...);
    }
}
