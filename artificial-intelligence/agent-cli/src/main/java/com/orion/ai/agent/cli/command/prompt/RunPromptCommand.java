package com.orion.ai.agent.cli.command.prompt;

import com.orion.ai.agent.cli.Agent;
import com.orion.ai.agent.cli.configuration.OrionConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class RunPromptCommand
{
    @Autowired
    private OrionConfiguration config;
    @Autowired
    private Agent agent;


    @Command(name = "/p", description = "Run an agent prompt")
    public String runPrompt(@Option(longName = "prompt", shortName = 'p') String prompt)
    {
        OrionConfiguration.Openrouter.Api api = config.getOpenrouter().getApi();
        OrionConfiguration.Openrouter.Ai ai = config.getOpenrouter().getAi();
        String response = agent.prompt(api.getKey(), api.getBaseUrl(), ai.getModelId(), prompt);
        System.out.println("running model " + ai.getModelId());
        return response;
    }
}
