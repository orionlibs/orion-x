package com.orion.ai.agent.cli.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.orion.util.shell.BashCommandRunner;

@JsonClassDescription("Execute a shell command")
public class RunBashCommandTool implements Tool
{
    @JsonPropertyDescription("The command to execute")
    public String command;


    public String execute()
    {
        return BashCommandRunner.run(command);
    }
}