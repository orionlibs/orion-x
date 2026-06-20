package com.orion.ai.agent.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ToolsRegistryTest
{
    @Test
    void getReturnsReadFileClass()
    {
        assertThat(ToolsRegistry.get("ReadFile")).isEqualTo(ReadFile.class);
    }


    @Test
    void getReturnsWriteFileClass()
    {
        assertThat(ToolsRegistry.get("WriteFile")).isEqualTo(WriteFile.class);
    }


    @Test
    void getReturnsRunBashCommandClass()
    {
        assertThat(ToolsRegistry.get("RunBashCommand")).isEqualTo(RunBashCommand.class);
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
                        .containsExactlyInAnyOrder(ReadFile.class, WriteFile.class, RunBashCommand.class);
    }
}
