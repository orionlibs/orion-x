package com.orion.ai.agent.cli.command.agent;

import com.orion.util.logger.Logger;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class SelectAgentCommand
{
    @Command(name = "/agent", description = "Select an agent")
    public String selectAgent(@Option(longName = "agent", shortName = 'a') String agentID)
    {
        Logger.info("Selected " + agentID);
        return agentID;
    }
}
