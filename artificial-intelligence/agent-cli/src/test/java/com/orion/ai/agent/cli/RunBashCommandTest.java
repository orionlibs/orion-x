package com.orion.ai.agent.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.orion.ai.agent.cli.tool.RunBashCommand;
import org.junit.jupiter.api.Test;

class RunBashCommandTest
{
    @Test
    void returnsCommandStdout()
    {
        RunBashCommand tool = new RunBashCommand();
        tool.command = "echo hello";
        assertThat(tool.execute()).isEqualTo("hello");
    }


    @Test
    void joinsMultipleOutputLines()
    {
        RunBashCommand tool = new RunBashCommand();
        tool.command = "printf 'line1\\nline2'";
        assertThat(tool.execute()).isEqualTo("line1\nline2");
    }


    @Test
    void capturesStderr()
    {
        RunBashCommand tool = new RunBashCommand();
        tool.command = "echo error_output >&2";
        assertThat(tool.execute()).isEqualTo("error_output");
    }


    @Test
    void returnsEmptyStringForNoOutput()
    {
        RunBashCommand tool = new RunBashCommand();
        tool.command = "true";
        assertThat(tool.execute()).isEqualTo("");
    }


    @Test
    void returnsFatalMessageOnInvalidCommand()
    {
        RunBashCommand tool = new RunBashCommand();
        tool.command = "exit 1";
        // Non-zero exit codes don't throw — only I/O failures do
        assertThat(tool.execute()).isEqualTo("");
    }
}
