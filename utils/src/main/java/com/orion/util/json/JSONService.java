package com.orion.util.json;

import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

public final class JSONService
{
    private JSONService()
    {
    }


    public static String convertObjectToJSON(Object objectToConvert)
    {
        try
        {
            return JSONMapper.getMapper().writeValueAsString(objectToConvert);
        }
        catch(JacksonException e)
        {
            return "";
        }
    }


    public static JsonNode convertJSONToNode(byte[] jsonData) throws JacksonException
    {
        return JSONMapper.getMapper().readTree(jsonData);
    }


    public static JsonNode convertJSONToNode(String jsonData) throws JacksonException
    {
        return JSONMapper.getMapper().readTree(jsonData);
    }


    public static Object convertJSONToObject(String jsonData, Class<?> classToConvertTo) throws JacksonException
    {
        return JSONMapper.getMapper().readValue(jsonData, classToConvertTo);
    }


    public static Object convertJSONToObject(String jsonData, TypeReference<?> classToConvertTo) throws JacksonException
    {
        return JSONMapper.getMapper().readValue(jsonData, classToConvertTo);
    }


    public static Map<String, Object> convertNodeToMap(JsonNode node) throws JacksonException
    {
        return JSONMapper.getMapper().treeToValue(node, new TypeReference<Map<String, Object>>()
        {
        });
    }
}
