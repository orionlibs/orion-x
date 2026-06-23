package com.orion.ai.agent.cli.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.orion.ai.agent.cli.tool.Tool;
import com.orion.ai.agent.cli.tool.ToolsRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Agent
{
    public static String SELECTED_AGENT = "NONE";
    @Autowired
    private AgentValidator agentValidator;
    private OpenAIClient client;
    private ContextBuilder contextBuilder;


    public String prompt(String apiKey, String baseUrl, String modelId, String prompt)
    {
        initialise(apiKey, baseUrl, modelId);
        contextBuilder.addMessage(MessageFactory.user(prompt));
        AgentReply reply = reason();
        if(reply.shouldStop)
        {
            //Logger.debug("No further tool calls requested.\n");
            return reply.answer;
        }
        return "I am unable to come to an answer.";
    }


    private void initialise(String apiKey, String baseUrl, String modelId)
    {
        agentValidator.validate(apiKey, baseUrl, modelId);
        client = OpenAIOkHttpClient.builder()
                                   .apiKey(apiKey)
                                   .baseUrl(baseUrl)
                                   .build();
        contextBuilder = new ContextBuilder(modelId);
        for(Class<?> tool : ToolsRegistry.getAll())
        {
            contextBuilder.addTool(tool);
        }
    }


    private AgentReply reason()
    {
        ChatCompletion response = client.chat().completions().create(contextBuilder.build());
        List<ChatCompletion.Choice> choices;
        try
        {
            choices = response.choices();
        }
        catch(OpenAIInvalidDataException e)
        {
            throw new RuntimeException("Model returned a malformed response — `choices` field missing. " + "The model may be unavailable or returning a non-standard error payload. " + "SDK message: " + e.getMessage(), e);
        }
        if(choices.isEmpty())
        {
            throw new RuntimeException("no choices in response");
        }
        return processChoice(choices.getFirst());
    }


    private AgentReply processChoice(ChatCompletion.Choice choice)
    {
        String modelReply = choice.message().content().orElse("Reply is empty");
        if(choice.message().toolCalls().isEmpty() || choice.message().toolCalls().get().isEmpty())
        {
            return AgentReply.stop(modelReply);
        }
        //Logger.info("Model reply: " + modelReply + "\n");
        List<ChatCompletionMessageToolCall> toolCalls = choice.message().toolCalls().get();
        contextBuilder.addMessage(MessageFactory.assistant(toolCalls));
        for(ChatCompletionMessageToolCall toolCall : toolCalls)
        {
            callTool(toolCall);
        }
        return AgentReply.keepGoing();
    }


    private void callTool(ChatCompletionMessageToolCall toolCall)
    {
        ChatCompletionMessageFunctionToolCall.Function fn = toolCall.asFunction().function();
        String result = executeTool(fn.name(), fn.arguments());
        //Logger.debug("Tool [" + fn.name() + "] output: <<\n\n" + result + "\n\n>>");
        contextBuilder.addMessage(MessageFactory.tool(toolCall.asFunction().id(), result));
    }


    protected String executeTool(String toolName, String jsonArguments)
    {
        try
        {
            Class<Tool> toolClass = ToolsRegistry.get(toolName);
            Tool tool = new ObjectMapper().readValue(jsonArguments, toolClass);
            return tool.execute();
        }
        catch(Exception e)
        {
            throw new RuntimeException("Failed to execute tool " + toolName + ": " + e.getMessage(), e);
        }
    }
}
