package com.orion.ai.agent.cli.command.prompt;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class RunPromptCommand
{
    @Command(name = "run.prompt", description = "Run an agent prompt")
    public String runPrompt(@Option(longName = "prompt", shortName = 'p') String prompt)
    {
        return prompt;
    }
}
