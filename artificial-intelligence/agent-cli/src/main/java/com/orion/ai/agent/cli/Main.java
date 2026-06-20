package com.orion.ai.agent.cli;

public class Main
{
    private static final Logger logger = new Logger();


    void main(String[] args)
    {
        var parameters = new ProgramParameters(args);
        var agent = new Agent(parameters.getApiKey(), parameters.getBaseUrl());
        String reply = agent.prompt(parameters.getPrompt());
        logger.info(reply);
    }
}
