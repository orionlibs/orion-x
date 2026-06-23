package com.orion.ai.agent.cli.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MockAgentTest
{
    @Test
    void returnsMappedResponseForKnownPrompt()
    {
        MockAgent agent = new MockAgent(Map.of("list files", "file1.txt\nfile2.txt"));
        assertThat(agent.prompt("key", "url", "mock-agent", "list files"))
                        .isEqualTo("file1.txt\nfile2.txt");
    }


    @Test
    void returnsDefaultResponseForUnknownPrompt()
    {
        MockAgent agent = new MockAgent(Map.of("list files", "file1.txt"));
        assertThat(agent.prompt("key", "url", "mock-agent", "unknown prompt"))
                        .isEqualTo("This is a mock response. No matching response configured for this prompt.");
    }


    @Test
    void returnsCustomDefaultResponseWhenOverridden()
    {
        MockAgent agent = new MockAgent(Map.of(), "custom fallback");
        assertThat(agent.prompt("key", "url", "mock-agent", "anything"))
                        .isEqualTo("custom fallback");
    }
}
