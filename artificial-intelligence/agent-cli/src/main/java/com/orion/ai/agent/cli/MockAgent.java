package com.orion.ai.agent.cli;

import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class MockAgent extends Agent
{
    private static final String DEFAULT_RESPONSE =
                    "This is a mock response. No matching response configured for this prompt.";

    private final Map<String, String> responses;
    private final String defaultResponse;


    public MockAgent()
    {
        this.responses = Map.of();
        this.defaultResponse = DEFAULT_RESPONSE;
    }


    public MockAgent(Map<String, String> responses)
    {
        this.responses = responses;
        this.defaultResponse = DEFAULT_RESPONSE;
    }


    public MockAgent(Map<String, String> responses, String defaultResponse)
    {
        this.responses = responses;
        this.defaultResponse = defaultResponse;
    }


    @Override
    public String prompt(String apiKey, String baseUrl, String modelId, String prompt)
    {
        if("mock-agent".equals(modelId))
        {
            return responses.getOrDefault(prompt, defaultResponse);
        }
        return super.prompt(apiKey, baseUrl, modelId, prompt);
    }
}
