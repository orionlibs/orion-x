package com.orion.mqtt.client;

public class DataRecord
{
    private String rawData;


    public DataRecord(String rawData)
    {
        this.rawData = rawData;
    }


    public String getRawData()
    {
        return rawData;
    }


    @Override
    public String toString()
    {
        return rawData;
    }
}
