package com.orion.ai.agent.cli.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.orion.ai.agent.cli.core.Tool;
import java.io.FileOutputStream;
import java.io.IOException;

@JsonClassDescription("Write content to a file. If the file doesn't exist, it's created. If it exists, it's overwritten with the new content.")
public class WriteFile implements Tool
{
    @JsonPropertyDescription("The path of the file to write to")
    public String file_path;
    @JsonPropertyDescription("The content to write to the file")
    public String content;


    public String execute()
    {
        try(FileOutputStream out = new FileOutputStream(file_path))
        {
            out.write(content.getBytes());
            return "Successfully wrote requested content to " + file_path;
        }
        catch(IOException e)
        {
            throw new RuntimeException("Failed to execute WriteFile tool: " + e.getMessage(), e);
        }
    }
}