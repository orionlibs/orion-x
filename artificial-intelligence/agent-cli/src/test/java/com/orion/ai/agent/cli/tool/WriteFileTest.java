package com.orion.ai.agent.cli.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WriteFileTest
{
    @TempDir
    Path tempDir;


    @Test
    void writesContentToNewFile() throws IOException
    {
        Path file = tempDir.resolve("output.txt");
        WriteFile tool = new WriteFile();
        tool.file_path = file.toString();
        tool.content = "test content";
        String result = tool.execute();
        assertThat(result).contains(file.toString());
        assertThat(Files.readString(file)).isEqualTo("test content");
    }


    @Test
    void overwritesExistingFile() throws IOException
    {
        Path file = Files.writeString(tempDir.resolve("existing.txt"), "old content");
        WriteFile tool = new WriteFile();
        tool.file_path = file.toString();
        tool.content = "new content";
        tool.execute();
        assertThat(Files.readString(file)).isEqualTo("new content");
    }


    @Test
    void throwsOnInvalidPath()
    {
        WriteFile tool = new WriteFile();
        tool.file_path = "/nonexistent/dir/file.txt";
        tool.content = "content";
        assertThatThrownBy(tool::execute)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Failed to execute WriteFile tool");
    }
}
