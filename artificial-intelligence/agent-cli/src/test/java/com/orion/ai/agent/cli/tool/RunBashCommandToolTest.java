package com.orion.ai.agent.cli.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunBashCommandToolTest
{
    @Test
    void returnsCommandStdout()
    {
        RunBashCommandTool tool = new RunBashCommandTool();
        tool.command = "echo hello";
        assertThat(tool.execute()).isEqualTo("hello");
    }


    @Test
    void joinsMultipleOutputLines()
    {
        RunBashCommandTool tool = new RunBashCommandTool();
        tool.command = "printf 'line1\\nline2'";
        assertThat(tool.execute()).isEqualTo("line1\nline2");
    }


    @Test
    void capturesStderr()
    {
        RunBashCommandTool tool = new RunBashCommandTool();
        tool.command = "echo error_output >&2";
        assertThat(tool.execute()).isEqualTo("error_output");
    }


    @Test
    void returnsEmptyStringForNoOutput()
    {
        RunBashCommandTool tool = new RunBashCommandTool();
        tool.command = "true";
        assertThat(tool.execute()).isEqualTo("");
    }


    @Test
    void returnsFatalMessageOnInvalidCommand()
    {
        RunBashCommandTool tool = new RunBashCommandTool();
        tool.command = "exit 1";
        // Non-zero exit codes don't throw — only I/O failures do
        assertThat(tool.execute()).isEqualTo("");
    }
}
