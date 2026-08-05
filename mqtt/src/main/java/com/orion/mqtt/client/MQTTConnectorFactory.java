package com.orion.mqtt.client;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import java.util.function.Consumer;

public class MQTTConnectorFactory
{
    public MQTTBlockingClient newBlockingMQTTConnector(String brokerUrl, int port, String clientId)
    {
        return new MQTTBlockingClient(brokerUrl, port, clientId);
    }


    public MQTTAsynchronousPublisherClient newAsynchronousMQTTConnectorForPublisher(String brokerUrl, int port, String topic, String payload, String clientId)
    {
        return new MQTTAsynchronousPublisherClient(brokerUrl, port, topic, payload, clientId);
    }


    public MQTTAsynchronousPublisherClient newAsynchronousMQTTConnectorForPublisher(String brokerUrl, int port, String clientId)
    {
        return new MQTTAsynchronousPublisherClient(brokerUrl, port, clientId);
    }


    public MQTTAsynchronousPublisherClientWithCredentials newAsynchronousMQTTConnectorForPublisherWithCredentials(String brokerUrl, int port, String clientId, String username, String password)
    {
        return new MQTTAsynchronousPublisherClientWithCredentials(brokerUrl, port, clientId, username, password);
    }


    public MQTTAsynchronousSubscriberClient newAsynchronousMQTTConnectorForSubscriber(String brokerUrl, int port, String topic, MqttQos qualityOfServiceLevel, String clientId)
    {
        return new MQTTAsynchronousSubscriberClient(brokerUrl, port, topic, qualityOfServiceLevel, clientId);
    }


    public MQTTAsynchronousSubscriberClient newAsynchronousMQTTConnectorForSubscriber(String brokerUrl, int port, String topic, MqttQos qualityOfServiceLevel, String clientId, Consumer<Mqtt5Publish> callback)
    {
        return new MQTTAsynchronousSubscriberClient(brokerUrl, port, topic, qualityOfServiceLevel, clientId, callback);
    }


    public MQTTAsynchronousSubscriberClientWithCredentials newAsynchronousMQTTConnectorForSubscriberWithCredentials(String brokerUrl, int port, String topic, MqttQos qualityOfServiceLevel, String clientId, String username, String password)
    {
        return new MQTTAsynchronousSubscriberClientWithCredentials(brokerUrl, port, topic, qualityOfServiceLevel, clientId, username, password);
    }


    public MQTTAsynchronousUnsubscriberClient newAsynchronousMQTTConnectorForUnsubscriber(String brokerUrl, int port, String topic, String clientId)
    {
        return new MQTTAsynchronousUnsubscriberClient(brokerUrl, port, topic, clientId);
    }
}
