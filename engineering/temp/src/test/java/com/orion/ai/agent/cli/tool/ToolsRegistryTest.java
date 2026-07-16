package com.orion.ai.agent.cli.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ToolsRegistryTest
{
    @Test
    void getReturnsReadFileClass()
    {
        assertThat(ToolsRegistry.get("ReadFileTool")).isEqualTo(ReadFileTool.class);
    }


    @Test
    void getReturnsWriteFileClass()
    {
        assertThat(ToolsRegistry.get("WriteFileTool")).isEqualTo(WriteFileTool.class);
    }


    @Test
    void getReturnsRunBashCommandClass()
    {
        assertThat(ToolsRegistry.get("RunBashCommandTool")).isEqualTo(RunBashCommandTool.class);
    }


    @Test
    void getThrowsForUnknownTool()
    {
        assertThatThrownBy(() -> ToolsRegistry.get("UnknownTool"))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unknown function: UnknownTool");
    }


    @Test
    void getAllReturnsAllThreeTools()
    {
        assertThat(ToolsRegistry.getAll())
                        .hasSize(3)
                        .containsExactlyInAnyOrder(ReadFileTool.class, WriteFileTool.class, RunBashCommandTool.class);
    }
}
