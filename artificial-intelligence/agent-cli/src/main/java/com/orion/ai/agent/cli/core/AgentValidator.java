package com.orion.ai.agent.cli.core;

import com.orion.util.logger.Logger;
import org.springframework.stereotype.Component;

@Component
public class AgentValidator
{
    void validate(String apiKey, String baseUrl, String modelId)
    {
        if(apiKey == null || apiKey.isBlank())
        {
            Logger.error("The API key for openrouter cannot be empty");
        }
        if(baseUrl == null || baseUrl.isBlank())
        {
            Logger.error("The API base URL for openrouter cannot be empty");
        }
        if(modelId == null || modelId.isBlank())
        {
            Logger.error("The openrouter requires a modelID");
        }
    }
}
