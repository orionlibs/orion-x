package com.orion.ai.agent.cli.command.log;

import com.orion.util.logger.Logger;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class LogInfoCommand
{
    @Command(name = "log.info", description = "Log a message at INFO level")
    public String logInfo(@Option(longName = "message", shortName = 'm') String message)
    {
        Logger.info(message);
        return message;
    }
}
