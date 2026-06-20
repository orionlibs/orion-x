package com.orion.ai.agent.cli.command.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogInfoCommandTest
{
    @Test
    void logInfoReturnsMessage()
    {
        LogInfoCommand cmd = new LogInfoCommand();
        assertThat(cmd.logInfo("test message")).isEqualTo("test message");
    }


    @Test
    void logInfoReturnsEmptyString()
    {
        LogInfoCommand cmd = new LogInfoCommand();
        assertThat(cmd.logInfo("")).isEqualTo("");
    }
}
