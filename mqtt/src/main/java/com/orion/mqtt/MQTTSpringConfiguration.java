package com.orion.mqtt;

import com.orion.mqtt.server.MQTTBrokerServer;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MQTTSpringConfiguration
{
    @Bean(name = "MQTT5BrokerServer", destroyMethod = "stopBroker")
    public MQTTBrokerServer mqttBrokerServer() throws URISyntaxException, ExecutionException, InterruptedException
    {
        MQTTBrokerServer broker = new MQTTBrokerServer();
        broker.startBroker(false, false);
        return broker;
    }
}