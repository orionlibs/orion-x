package com.orion.ai.agent.cli.command.log;

import com.orion.util.logger.Logger;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class LogErrorCommand
{
    @ShellMethod(key = "log.error")
    public String logError(@ShellOption(value = {"--message", "-m"}) String message)
    {
        Logger.error(message);
        return null;
    }
}
