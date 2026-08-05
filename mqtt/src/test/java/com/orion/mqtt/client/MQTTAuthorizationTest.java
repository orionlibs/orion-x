package com.orion.mqtt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.orion.mqtt.ATest;
import com.orion.mqtt.ListLogHandler;
import com.orion.mqtt.Utils;
import com.orion.mqtt.server.MQTTAuthorizationProvider;
import com.orion.mqtt.server.MQTTBrokerServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
//@Execution(ExecutionMode.CONCURRENT)
public class MQTTAuthorizationTest extends ATest
{
    private ListLogHandler listLogHandler;
    private MQTTBrokerServer brokerServer;
    private Mqtt5AsyncClient testPublisherClient;
    private Mqtt5AsyncClient testSubscriberClient;
    private String clientID = "testClientId";


    @BeforeEach
    void setUp() throws Exception
    {
        listLogHandler = new ListLogHandler();
        MQTTAuthorizationProvider.addLogHandler(listLogHandler);
        brokerServer = new MQTTBrokerServer();
        brokerServer.startBroker(true, true);
        Utils.nonblockingDelay(3);
    }


    @AfterEach
    void teardown()
    {
        MQTTAuthorizationProvider.removeLogHandler(listLogHandler);
        if(testPublisherClient != null && testPublisherClient.getConfig().getState().isConnectedOrReconnect())
        {
            testPublisherClient.disconnect();
        }
        if(testSubscriberClient != null && testSubscriberClient.getConfig().getState().isConnectedOrReconnect())
        {
            testSubscriberClient.disconnect();
        }
        brokerServer.stopBroker();
    }


    @Test
    void testClientAuthorization()
    {
        startSubscriberClient(clientID, "test/topic1", MqttQos.EXACTLY_ONCE, "admin", "password");
        Utils.nonblockingDelay(2);
        assertEquals(0, listLogHandler.getLogRecords().size());
        startSubscriberClient(clientID, "forbidden/topic1", MqttQos.EXACTLY_ONCE, "admin", "password");
        Utils.nonblockingDelay(2);
        assertEquals(1, listLogHandler.getLogRecords().size());
        startPublisherClient(clientID, "admin", "password");
        Utils.nonblockingDelay(2);
        testPublisherClient.publishWith().topic("test/topic1").payload("somePayload1".getBytes()).send();
        assertEquals(1, listLogHandler.getLogRecords().size());
        Utils.nonblockingDelay(2);
        testPublisherClient.publishWith().topic("forbidden/topic1").payload("somePayload1".getBytes()).send();
        Utils.nonblockingDelay(2);
        assertEquals(2, listLogHandler.getLogRecords().size());
        assertTrue(listLogHandler.getLogRecords()
                        .stream()
                        .anyMatch(record -> record.getMessage().contains("forbidden")));
    }


    private void startPublisherClient(String clientId, String username, String password)
    {
        this.testPublisherClient = new MQTTConnectorFactory().newAsynchronousMQTTConnectorForPublisherWithCredentials("0.0.0.0", 1883, clientId, username, password).getClient();
    }


    private void startSubscriberClient(String clientId, String topic, MqttQos qualityOfServiceLevel, String username, String password)
    {
        this.testSubscriberClient = new MQTTConnectorFactory().newAsynchronousMQTTConnectorForSubscriberWithCredentials("0.0.0.0", 1883, topic, qualityOfServiceLevel, clientId, username, password).getClient();
    }
}
