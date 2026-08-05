package com.orion.engineering.digitaltwin.sensor_simulator.sensor;

import static org.assertj.core.api.Assertions.assertThat;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.orion.mqtt.client.MQTTConnectorFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class TemperatureSensorSimulatorTest
{
    private static final String BROKER_HOST = "localhost";
    private static final int BROKER_PORT = 1883;
    private static final long TEST_DURATION_MILLIS = 5000;
    private final JsonMapper jsonMapper = new JsonMapper();
    private final List<Double> receivedTemperatures = new CopyOnWriteArrayList<>();
    private Mqtt5AsyncClient subscriberClient;


    @BeforeEach
    void setUp()
    {
        subscriberClient = new MQTTConnectorFactory().newAsynchronousMQTTConnectorForSubscriber(BROKER_HOST, BROKER_PORT, TemperatureSensorSimulator.TOPIC, MqttQos.AT_LEAST_ONCE, "temperature-sensor-test-subscriber",
                                                                     publish -> receivedTemperatures.add(jsonMapper.readTree(publish.getPayloadAsBytes()).get("temperatureCelsius").asDouble()))
                                                     .getClient();
    }


    @AfterEach
    void tearDown()
    {
        if(subscriberClient != null && subscriberClient.getConfig().getState().isConnectedOrReconnect())
        {
            subscriberClient.disconnect();
        }
    }


    @Test
    @DisplayName("When the temperature sensor simulator runs for 5 seconds, then every received temperature exists and is between 0 and 100")
    void test() throws InterruptedException
    {
        Thread.sleep(TEST_DURATION_MILLIS);
        assertThat(receivedTemperatures).isNotEmpty();
        assertThat(receivedTemperatures).allSatisfy(temperature -> assertThat(temperature).isBetween(0.0, 100.0));
    }
}
