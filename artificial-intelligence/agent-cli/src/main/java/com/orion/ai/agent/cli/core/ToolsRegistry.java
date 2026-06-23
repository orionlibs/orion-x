package com.orion.ai.agent.cli.core;

import com.orion.ai.agent.cli.tool.ReadFile;
import com.orion.ai.agent.cli.tool.RunBashCommand;
import com.orion.ai.agent.cli.tool.WriteFile;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ToolsRegistry
{
    private static final Map<String, Class<?>> registry = new HashMap<>()
    {{
        put(ReadFile.class.getSimpleName(), ReadFile.class);
        put(WriteFile.class.getSimpleName(), WriteFile.class);
        put(RunBashCommand.class.getSimpleName(), RunBashCommand.class);
    }};


    @SuppressWarnings("unchecked")
    public static Class<Tool> get(String toolName)
    {
        if(!registry.containsKey(toolName))
        {
            throw new IllegalArgumentException("Unknown function: " + toolName);
        }
        return (Class<Tool>)registry.get(toolName);
    }


    public static Collection<Class<?>> getAll()
    {
        return registry.values();
    }
}
