package com.orion.ai.agent.cli.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonClassDescription("Read and return the contents of a file")
public class ReadFileTool implements Tool
{
    @JsonPropertyDescription("The path to the file to read")
    public String file_path;


    public String execute()
    {
        try(Stream<String> lines = Files.lines(Paths.get(file_path)))
        {
            return lines.collect(Collectors.joining("\n"));
        }
        catch(IOException e)
        {
            throw new RuntimeException("Failed to execute ReadFile tool: " + e.getMessage(), e);
        }
    }
}