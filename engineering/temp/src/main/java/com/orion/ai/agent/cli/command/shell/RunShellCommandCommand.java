package com.orion.ai.agent.cli.command.shell;

import com.orion.util.shell.BashCommandRunner;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class RunShellCommandCommand
{
    @Command(name = "run", description = "Runs a BASH command in the local machine")
    public String runShellCommand(@Option(longName = "command", shortName = 'c') String command)
    {
        return BashCommandRunner.run(command);
    }
}
