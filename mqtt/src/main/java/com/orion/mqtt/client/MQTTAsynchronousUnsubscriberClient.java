package com.orion.mqtt.client;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;

public class MQTTAsynchronousUnsubscriberClient
{
    private Mqtt5AsyncClient client;


    public MQTTAsynchronousUnsubscriberClient(String brokerUrl, int port, String topic, String clientId)
    {
        this.client = Mqtt5Client.builder()
                                 .identifier(clientId)
                                 .serverHost(brokerUrl)
                                 .serverPort(port)
                                 .buildAsync();
        client.connect()
              .thenCompose(connAck -> {
                  System.out.println("Successfully connected unsubscriber!");
                  return client.unsubscribeWith().topicFilter(topic).send();
              }).exceptionally(throwable -> {
                  System.out.println("Something went wrong unsubscriber: " + throwable.getMessage());
                  return null;
              });
    }


    public Mqtt5AsyncClient getClient()
    {
        return client;
    }
}
