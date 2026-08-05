package com.orion.mqtt.client;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import java.nio.charset.StandardCharsets;

public class MQTTAsynchronousPublisherClientWithCredentials
{
    private Mqtt5AsyncClient client;


    public MQTTAsynchronousPublisherClientWithCredentials(String brokerUrl, int port, String clientId, String username, String password)
    {
        this.client = Mqtt5Client.builder()
                                 .identifier(clientId)
                                 .simpleAuth(Mqtt5SimpleAuth.builder().username(username).password(password.getBytes(StandardCharsets.UTF_8)).build())
                                 .serverHost(brokerUrl)
                                 .serverPort(port)
                                 .buildAsync();
        client.connect();
    }


    public Mqtt5AsyncClient getClient()
    {
        return client;
    }
}
