package com.orion.ai.agent.cli.command.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogErrorCommandTest
{
    @Test
    void logErrorReturnsMessage()
    {
        LogErrorCommand cmd = new LogErrorCommand();
        assertThat(cmd.logError("error message")).isEqualTo("error message");
    }


    @Test
    void logErrorReturnsEmptyString()
    {
        LogErrorCommand cmd = new LogErrorCommand();
        assertThat(cmd.logError("")).isEqualTo("");
    }
}
