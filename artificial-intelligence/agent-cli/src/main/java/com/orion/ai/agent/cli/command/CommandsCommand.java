package com.orion.ai.agent.cli.command;

import com.orion.util.json.JSONService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
public class CommandsCommand
{
    @Command(name = "commands", description = "List all available commands with their AI instructions")
    public String commands() throws IOException
    {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:commands-instructions/*.md");
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        List<Map<String, String>> commands = new ArrayList<>();
        for(Resource resource : resources)
        {
            String filename = resource.getFilename();
            String commandName = filename.substring(0, filename.length() - 3).replace('-', '.');
            String instructions = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("command", commandName);
            entry.put("instructions", instructions);
            commands.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commands", commands);
        return JSONService.convertObjectToJSON(result);
    }
}
