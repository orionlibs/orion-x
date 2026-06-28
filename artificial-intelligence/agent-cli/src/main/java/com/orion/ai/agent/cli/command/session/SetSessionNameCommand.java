package com.orion.ai.agent.cli.command.session;

import com.orion.ai.agent.cli.core.Session;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class SetSessionNameCommand
{
    @Command(name = "session.name", description = "Set this session's name")
    public String selectAgent(@Option(longName = "name", shortName = 'n') String sessionName)
    {
        if(sessionName == null || sessionName.isBlank())
        {
            return Session.sessionName;
        }
        else
        {
            Session.sessionName = sessionName;
            return sessionName;
        }
    }
}
