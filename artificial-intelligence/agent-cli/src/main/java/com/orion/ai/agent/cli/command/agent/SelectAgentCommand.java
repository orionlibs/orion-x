package com.orion.ai.agent.cli.command.agent;

import com.orion.ai.agent.cli.configuration.Config;
import com.orion.ai.agent.cli.core.Agent;
import com.orion.util.shell.cli.InteractiveSelectorInCLI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class SelectAgentCommand
{
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
            if(Config.config.getAi().getAgents().containsKey(agentID))
            {
                Agent.SELECTED_AGENT = Config.config.getAi().getAgents().get(agentID);
                return "Agent set to " + agentID;
            }
            else
            {
                return "Invalid selection";
            }
        }
    }


    private String selectInteractively()
    {
        String selected = interactiveSelectorInCLI.select("Select Agent", Config.config.getAi().getAgents().keySet().stream().toList());
        if(selected == null)
        {
            return "Invalid selection";
        }
        Agent.SELECTED_AGENT = Config.config.getAi().getAgents().get(selected);
        return "Agent set to " + selected;
    }
}
