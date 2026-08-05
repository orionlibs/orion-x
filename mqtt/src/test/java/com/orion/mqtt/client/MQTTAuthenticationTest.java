package com.orion.mqtt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.orion.mqtt.ATest;
import com.orion.mqtt.Utils;
import com.orion.mqtt.server.MQTTBrokerServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
//@Execution(ExecutionMode.CONCURRENT)
public class MQTTAuthenticationTest extends ATest
{
    private MQTTBrokerServer brokerServer;
    private Mqtt5AsyncClient testPublisherClient;
    private String clientID = "testClientId";


    @BeforeEach
    void setUp() throws Exception
    {
        brokerServer = new MQTTBrokerServer();
        brokerServer.startBroker(true, false);
        Utils.nonblockingDelay(3);
    }


    @AfterEach
    void teardown()
    {
        if(testPublisherClient != null && testPublisherClient.getConfig().getState().isConnectedOrReconnect())
        {
            testPublisherClient.disconnect();
        }
        brokerServer.stopBroker();
    }


    @Test
    void testClientAuthentication()
    {
        startPublisherClient(clientID, "admin", "password");
        Utils.nonblockingDelay(2);
        assertTrue(testPublisherClient.getState().isConnectedOrReconnect());
        Utils.nonblockingDelay(2);
        startPublisherClient(clientID, "admin", "wrongpassword");
        assertFalse(testPublisherClient.getState().isConnectedOrReconnect());
        Utils.nonblockingDelay(2);
        startPublisherClient(clientID, "wronguser", "password");
        assertFalse(testPublisherClient.getState().isConnectedOrReconnect());
        Utils.nonblockingDelay(2);
        startPublisherClient(clientID, "wronguser", "wrongpassword");
        assertFalse(testPublisherClient.getState().isConnectedOrReconnect());
    }


    private void startPublisherClient(String clientId, String username, String password)
    {
        this.testPublisherClient = new MQTTConnectorFactory().newAsynchronousMQTTConnectorForPublisherWithCredentials("0.0.0.0", 1883, clientId, username, password).getClient();
    }
}
