package com.orion.ai.agent.cli.command.log;

import com.orion.util.logger.Logger;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class LogErrorCommand
{
    @Command(name = "log.error", description = "Log a message at ERROR level")
    public String logError(@Option(longName = "message", shortName = 'm') String message)
    {
        Logger.error(message);
        return message;
    }
}
