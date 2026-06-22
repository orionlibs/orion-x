package com.orion.ai.agent.cli.configuration;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "orion")
public class OrionConfiguration
{
    private Openrouter openrouter = new Openrouter();
    private Ai ai = new Ai();


    @Data
    public static class Ai
    {
        private Map<String, String> agents;
        private String defaultAgent;
    }


    @Data
    public static class Openrouter
    {
        private Openrouter.Api api = new Openrouter.Api();


        @Data
        public static class Api
        {
            private String key;
            private String baseUrl;
        }
    }
}
