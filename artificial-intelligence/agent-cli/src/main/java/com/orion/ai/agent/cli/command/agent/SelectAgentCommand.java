package com.orion.ai.agent.cli.command.agent;

import com.orion.util.logger.Logger;
import com.orion.util.shell.cli.InteractiveSelectorInCLI;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class SelectAgentCommand
{
    private static final List<String> AGENTS = List.of("claude", "OpenAI", "gemini");
    @Autowired
    private InteractiveSelectorInCLI interactiveSelectorInCLI;


    @Command(name = "/agent", description = "Select an agent")
    public String selectAgent(@Option(longName = "agent", shortName = 'a') String agentID)
    {
        if(agentID == null || agentID.isBlank())
        {
            return selectInteractively();
        }
        Logger.info("Agent set to " + agentID);
        return "Agent set to " + agentID;
    }


    private String selectInteractively()
    {
        String selected = interactiveSelectorInCLI.select("Select Agent", AGENTS);
        if(selected == null)
        {
            return "Invalid selection";
        }
        Logger.info("Selected " + selected);
        return "Agent set to: " + selected;
    }
}
