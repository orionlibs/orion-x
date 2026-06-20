package com.orion.ai.agent.cli.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "orion")
public class OrionConfiguration
{
    private Openrouter openrouter = new Openrouter();


    @Data
    public static class Openrouter
    {
        private Api api = new Api();
        private Ai ai = new Ai();


        @Data
        public static class Api
        {
            private String key;
            private String baseUrl;
        }


        @Data
        public static class Ai
        {
            private String modelId;
        }
    }
}
