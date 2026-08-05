package com.orion.mqtt.client;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RunningMQTTClients
{
    public static final ConcurrentMap<String, Mqtt5AsyncClient> idToClientMapper;

    static
    {
        idToClientMapper = new ConcurrentHashMap<>();
    }
}
