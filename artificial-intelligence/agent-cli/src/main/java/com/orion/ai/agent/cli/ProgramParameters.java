package com.orion.ai.agent.cli;

public class ProgramParameters
{
    private final String prompt;
    private final String apiKey = System.getenv("OPENROUTER_API_KEY");
    private final String baseUrl = System.getenv("OPENROUTER_BASE_URL");


    public ProgramParameters(String[] args)
    {
        if(apiKey == null || apiKey.isEmpty())
        {
            throw new RuntimeException("OPENROUTER_API_KEY env var is required");
        }
        prompt = parsePrompt(args);
        if(prompt == null || prompt.isEmpty())
        {
            throw new IllegalArgumentException("-p argument is required");
        }
    }


    public String getPrompt()
    {
        return prompt;
    }


    public String getApiKey()
    {
        return apiKey;
    }


    public String getBaseUrl()
    {
        if(baseUrl == null || baseUrl.isEmpty())
        {
            return "https://openrouter.ai/api/v1";
        }
        return baseUrl;
    }


    private String parsePrompt(String[] args)
    {
        for(int i = 0; i < args.length; i++)
        {
            if("-p".equals(args[i]) && i + 1 < args.length)
            {
                return args[i + 1];
            }
        }
        return "";
    }
}
