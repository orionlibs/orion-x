package com.orion.ai.agent.cli.command.prompt;

import com.orion.ai.agent.cli.Agent;
import com.orion.ai.agent.cli.configuration.OrionConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Arguments;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
public class RunPromptCommand
{
    @Autowired
    private OrionConfiguration config;
    @Autowired
    private Agent agent;


    @Command(name = "/p", description = "Run an agent prompt")
    public String runPrompt(@Arguments String[] words)
    {
        String prompt = String.join(" ", words);
        OrionConfiguration.Openrouter.Api api = config.getOpenrouter().getApi();
        OrionConfiguration.Openrouter.Ai ai = config.getOpenrouter().getAi();
        //System.out.println("running model " + ai.getModelId());
        String response = agent.prompt(api.getKey(), api.getBaseUrl(), ai.getModelId(), prompt);
        return response;
    }
}
