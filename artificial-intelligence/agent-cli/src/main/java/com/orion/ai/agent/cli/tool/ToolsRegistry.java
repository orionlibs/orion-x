package com.orion.ai.agent.cli.tool;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ToolsRegistry
{
    private static final Map<String, Class<?>> registry = new HashMap<>()
    {{
        put(ReadFileTool.class.getSimpleName(), ReadFileTool.class);
        put(WriteFileTool.class.getSimpleName(), WriteFileTool.class);
        put(RunBashCommandTool.class.getSimpleName(), RunBashCommandTool.class);
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
