package com.orion.ai.agent.cli.command.agent;

import com.orion.ai.agent.cli.Agent;
import com.orion.ai.agent.cli.configuration.OrionConfiguration;
import com.orion.util.logger.Logger;
import com.orion.util.shell.cli.InteractiveSelectorInCLI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class SelectAgentCommand
{
    @Autowired
    private OrionConfiguration config;
    @Autowired
    private InteractiveSelectorInCLI interactiveSelectorInCLI;


    @Command(name = "/agent", description = "Select an agent")
    public String selectAgent(@Option(longName = "agent", shortName = 'a') String agentID)
    {
        if(agentID == null || agentID.isBlank())
        {
            System.out.println("Currently selected agent is " + Agent.SELECTED_AGENT);
            return selectInteractively();
        }
        else
        {
            if(agentID == null)
            {
                return "Invalid selection";
            }
            else
            {
                if(config.getAi().getAvailableAgents().contains(agentID))
                {
                    Agent.SELECTED_AGENT = agentID;
                    Logger.info("Selected agent " + agentID);
                    return "Agent set to " + agentID;
                }
                else
                {
                    return "Invalid selection";
                }
            }
        }
    }


    private String selectInteractively()
    {
        String selected = interactiveSelectorInCLI.select("Select Agent", config.getAi().getAvailableAgents());
        if(selected == null)
        {
            return "Invalid selection";
        }
        Agent.SELECTED_AGENT = selected;
        Logger.info("Selected agent " + selected);
        return "Agent set to " + selected;
    }
}
