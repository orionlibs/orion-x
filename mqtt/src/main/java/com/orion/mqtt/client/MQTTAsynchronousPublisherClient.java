package com.orion.mqtt.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import java.util.concurrent.CompletableFuture;

public class MQTTAsynchronousPublisherClient
{
    private Mqtt5AsyncClient client;


    public MQTTAsynchronousPublisherClient(String brokerUrl, int port, String clientId)
    {
        this.client = Mqtt5Client.builder()
                                 .identifier(clientId)
                                 .serverHost(brokerUrl)
                                 .serverPort(port)
                                 .buildAsync();
        client.connect()
              .exceptionally(throwable -> {
                  System.out.println("Something went wrong publisher: " + throwable.getMessage());
                  return null;
              });
    }


    public MQTTAsynchronousPublisherClient(String brokerUrl, int port, String topic, String payload, String clientId)
    {
        this.client = Mqtt5Client.builder()
                                 .identifier(clientId)
                                 .serverHost(brokerUrl)
                                 .serverPort(port)
                                 .buildAsync();
        client.connect()
              .thenCompose(connAck -> {
                  System.out.println("Successfully connected publisher!");
                  final Mqtt5Publish publish = Mqtt5Publish.builder()
                                                           .topic(topic)
                                                           .payload(payload.getBytes(UTF_8))
                                                           .qos(MqttQos.EXACTLY_ONCE)
                                                           .build();
                  final var future = client.publish(publish);
                  return CompletableFuture.allOf(future);
              }).thenCompose(unused -> {
                  return client.disconnect();
              }).exceptionally(throwable -> {
                  System.out.println("Something went wrong publisher: " + throwable.getMessage());
                  return null;
              });
    }


    public Mqtt5AsyncClient getClient()
    {
        return client;
    }
}
