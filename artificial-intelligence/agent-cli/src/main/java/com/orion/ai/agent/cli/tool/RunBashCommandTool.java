package com.orion.ai.agent.cli.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.orion.ai.agent.cli.core.Tool;
import java.io.BufferedReader;
import java.io.IOException;

@JsonClassDescription("Execute a shell command")
public class RunBashCommandTool implements Tool
{
    @JsonPropertyDescription("The command to execute")
    public String command;


    public String execute()
    {
        try
        {
            Process process = new ProcessBuilder("/bin/sh", "-c", command)
                            .redirectErrorStream(true)
                            .start();
            process.waitFor();
            try(BufferedReader reader = process.inputReader())
            {
                return String.join("\n", reader.readAllLines());
            }
        }
        catch(IOException | InterruptedException e)
        {
            return "Fatal exception: " + e.getMessage();
        }
    }
}