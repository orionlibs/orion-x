package com.orion.ai.agent.cli.command.log;

import com.orion.util.logger.Logger;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class LogInfoCommand
{
    @ShellMethod(key = "log.info")
    public String logInfo(@ShellOption(value = {"--message", "-m"}) String message)
    {
        Logger.info(message);
        return null;
    }
}
