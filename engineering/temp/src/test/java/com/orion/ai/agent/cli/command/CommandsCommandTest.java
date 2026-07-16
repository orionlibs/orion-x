package com.orion.ai.agent.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class CommandsCommandTest
{
    @Test
    void commandsReturnsJsonContainingAllCommandNames() throws IOException
    {
        CommandsCommand cmd = new CommandsCommand();
        String result = cmd.commands();
        assertThat(result).contains("commands");
        assertThat(result).contains("log.info");
        assertThat(result).contains("log.error");
    }


    @Test
    void commandsReturnsValidJson() throws IOException
    {
        CommandsCommand cmd = new CommandsCommand();
        String result = cmd.commands();
        assertThat(result).startsWith("{");
        assertThat(result).endsWith("}");
    }


    @Test
    void commandsIncludesInstructionsForEachCommand() throws IOException
    {
        CommandsCommand cmd = new CommandsCommand();
        String result = cmd.commands();
        assertThat(result).contains("instructions");
    }
}
