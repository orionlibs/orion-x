package com.orion.mqtt.client;

import java.io.IOException;
import java.util.List;

public interface ConnectorConfigurator
{
    Errors validate();


    void storeToFile(String configFilePath) throws IOException;


    void storeToDatabase();


    class Errors
    {
        public List<String> errors;
    }
}
