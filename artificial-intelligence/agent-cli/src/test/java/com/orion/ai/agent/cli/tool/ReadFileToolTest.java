package com.orion.ai.agent.cli.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadFileToolTest
{
    @TempDir
    Path tempDir;


    @Test
    void readsFileContents() throws IOException
    {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello\nworld");
        ReadFileTool tool = new ReadFileTool();
        tool.file_path = file.toString();
        assertThat(tool.execute()).isEqualTo("hello\nworld");
    }


    @Test
    void readsSingleLineFile() throws IOException
    {
        Path file = tempDir.resolve("single.txt");
        Files.writeString(file, "only one line");
        ReadFileTool tool = new ReadFileTool();
        tool.file_path = file.toString();
        assertThat(tool.execute()).isEqualTo("only one line");
    }


    @Test
    void throwsOnMissingFile()
    {
        ReadFileTool tool = new ReadFileTool();
        tool.file_path = "/nonexistent/path/file.txt";
        assertThatThrownBy(tool::execute)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Failed to execute ReadFile tool");
    }
}
