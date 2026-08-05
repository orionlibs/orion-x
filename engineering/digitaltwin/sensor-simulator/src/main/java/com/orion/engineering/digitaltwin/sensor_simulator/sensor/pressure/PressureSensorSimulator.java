package com.orion.engineering.digitaltwin.sensor_simulator.sensor.pressure;

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
public class PressureSensorSimulator
{
    public static final String TOPIC = "sensors/pressure";
    private static final String BROKER_HOST = "localhost";
    private static final int BROKER_PORT = 1883;
    private static final String CLIENT_ID = "pressure-sensor-simulator";
    private static final double MIN_PRESSURE = 0.0;
    private static final double MAX_PRESSURE = 100.0;
    private final JsonMapper jsonMapper = new JsonMapper();
    private Mqtt5AsyncClient client;


    @PostConstruct
    void connect()
    {
        client = new MQTTConnectorFactory().newAsynchronousMQTTConnectorForPublisher(BROKER_HOST, BROKER_PORT, CLIENT_ID).getClient();
    }


    @Scheduled(fixedRate = 1000)
    void publishPressureReading()
    {
        if(client == null || !client.getConfig().getState().isConnected())
        {
            return;
        }
        double temperatureCelsius = ThreadLocalRandom.current().nextDouble(MIN_PRESSURE, MAX_PRESSURE);
        PressureReading reading = new PressureReading(temperatureCelsius, System.currentTimeMillis());
        Mqtt5Publish publish = Mqtt5Publish.builder()
                                           .topic(TOPIC)
                                           .payload(jsonMapper.writeValueAsBytes(reading))
                                           .qos(MqttQos.AT_LEAST_ONCE)
                                           .build();
        System.out.println("[pressure sensor MQTT] pressure = " + reading.pressure());
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
