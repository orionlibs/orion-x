package com.orion.engineering.digitaltwin.sensor_simulator.sensor.temperature;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.orion.mqtt.client.MQTTConnectorFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@DependsOn("MQTT5BrokerServer")
public class TemperatureSensorSimulator
{
    public static final String TOPIC = "sensors/temperature";
    private static final String BROKER_HOST = "localhost";
    private static final int BROKER_PORT = 1883;
    private static final String CLIENT_ID = "temperature-sensor-simulator";
    private static final double MIN_TEMPERATURE_CELSIUS = 0.0;
    private static final double MAX_TEMPERATURE_CELSIUS = 100.0;
    private final JsonMapper jsonMapper = new JsonMapper();
    private Mqtt5AsyncClient client;


    @PostConstruct
    void connect()
    {
        client = new MQTTConnectorFactory().newAsynchronousMQTTConnectorForPublisher(BROKER_HOST, BROKER_PORT, CLIENT_ID).getClient();
    }


    @Scheduled(fixedRate = 1000)
    void publishTemperatureReading()
    {
        if(client == null || !client.getConfig().getState().isConnected())
        {
            return;
        }
        double temperatureCelsius = ThreadLocalRandom.current().nextDouble(MIN_TEMPERATURE_CELSIUS, MAX_TEMPERATURE_CELSIUS);
        TemperatureReading reading = new TemperatureReading(temperatureCelsius, System.currentTimeMillis());
        Mqtt5Publish publish = Mqtt5Publish.builder()
                                           .topic(TOPIC)
                                           .payload(jsonMapper.writeValueAsBytes(reading))
                                           .qos(MqttQos.AT_LEAST_ONCE)
                                           .build();
        System.out.println("[temperature sensor MQTT] temperature = " + reading.temperatureCelsius());
        client.publish(publish);
    }


    @PreDestroy
    void disconnect()
    {
        if(client != null && client.getConfig().getState().isConnectedOrReconnect())
        {
            client.disconnect();
        }
    }
}
