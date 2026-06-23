package com.orion.ai.agent.cli.command.prompt;

import com.orion.ai.agent.cli.Agent;
import com.orion.ai.agent.cli.configuration.Config;
import com.orion.ai.agent.cli.configuration.OrionConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Arguments;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
public class RunPromptCommand
{
    @Autowired
    private Agent agent;


    @Command(name = "/p", description = "Run an agent prompt")
    public String runPrompt(@Arguments String[] words)
    {
        String prompt = String.join(" ", words);
        OrionConfiguration.Ai.Openrouter.Api api = Config.config.getAi().getOpenrouter().getApi();
        return agent.prompt(api.getKey(), api.getBaseUrl(), Agent.SELECTED_AGENT, prompt);
    }
}
