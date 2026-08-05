package com.orion.mqtt.client;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import java.nio.charset.StandardCharsets;

public class MQTTAsynchronousSubscriberClientWithCredentials
{
    private Mqtt5AsyncClient client;


    public MQTTAsynchronousSubscriberClientWithCredentials(String brokerUrl, int port, String topic, MqttQos qualityOfServiceLevel, String clientId, String username, String password)
    {
        this.client = Mqtt5Client.builder()
                                 .identifier(clientId)
                                 .simpleAuth(Mqtt5SimpleAuth.builder().username(username).password(password.getBytes(StandardCharsets.UTF_8)).build())
                                 .serverHost(brokerUrl)
                                 .serverPort(port)
                                 .buildAsync();
        /*client.publishes(MqttGlobalPublishFilter.SUBSCRIBED, publish -> {
            System.out.println("Received payload: " + new String(publish.getPayloadAsBytes(), UTF_8));
        });*/
        client.connect()
              .thenCompose(connAck -> {
                  System.out.println("Successfully connected subscriber!");
                  return client.subscribeWith().topicFilter(topic).qos(qualityOfServiceLevel).send();
              }).exceptionally(throwable -> {
                  System.out.println("Something went wrong subscriber: " + throwable.getMessage());
                  return null;
              });
    }


    public Mqtt5AsyncClient getClient()
    {
        return client;
    }
}
