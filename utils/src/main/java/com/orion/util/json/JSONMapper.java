package com.orion.util.json;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

final class JSONMapper
{
    private static ObjectMapper mapper;

    static
    {
        mapper = JsonMapper.builder()
                           //.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                           .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS, SerializationFeature.FAIL_ON_SELF_REFERENCES)
                           .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                           .build();
    }

    private JSONMapper()
    {
    }


    static ObjectMapper getMapper()
    {
        return mapper;
    }
}
