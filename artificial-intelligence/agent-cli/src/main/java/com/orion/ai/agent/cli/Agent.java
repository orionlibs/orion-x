package com.orion.ai.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Agent
{
    private static final int MAX_ITERATIONS = 5;
    private static final Logger logger = new Logger();
    public static String SELECTED_AGENT = "NONE";
    private OpenAIClient client;
    private ChatCompletionCreateParams.Builder contextBuilder;


    private void validate(String apiKey, String baseUrl, String modelId)
    {
        if(apiKey == null || apiKey.isBlank())
        {
            logger.error("The API key for openrouter cannot be empty");
        }
        if(baseUrl == null || baseUrl.isBlank())
        {
            logger.error("The API base URL for openrouter cannot be empty");
        }
        if(modelId == null || modelId.isBlank())
        {
            logger.error("The openrouter requires a modelID");
        }
    }


    public String prompt(String apiKey, String baseUrl, String modelId, String prompt)
    {
        initialise(apiKey, baseUrl, modelId);
        this.contextBuilder.addMessage(MessageFactory.user(prompt));
        int iterations = 0;
        while(iterations <= MAX_ITERATIONS)
        {
            logger.debug("\n====================================================\nIteration: " + iterations + "\n");
            Reply reply = reason();
            if(reply.shouldStop)
            {
                logger.debug("No further tool calls requested, stopping agent loop.\n");
                return reply.answer;
            }
            iterations += 1;
        }
        return "I have reached the max number of iterations and I am unable to come to an answer.";
    }


    private void initialise(String apiKey, String baseUrl, String modelId)
    {
        validate(apiKey, baseUrl, modelId);
        this.client = OpenAIOkHttpClient.builder()
                                        .apiKey(apiKey)
                                        .baseUrl(baseUrl)
                                        .build();
        this.contextBuilder = ChatCompletionCreateParams.builder()
                                                        .model(modelId);
        for(Class<?> tool : ToolsRegistry.getAll())
        {
            contextBuilder.addTool(tool);
        }
    }


    private Reply reason()
    {
        ChatCompletion response = client.chat().completions().create(this.contextBuilder.build());
        List<ChatCompletion.Choice> choices;
        try
        {
            choices = response.choices();
        }
        catch(OpenAIInvalidDataException e)
        {
            throw new RuntimeException(
                            "Model returned a malformed response — `choices` field missing. " +
                                            "The model may be unavailable or returning a non-standard error payload. " +
                                            "SDK message: " + e.getMessage(), e);
        }
        if(choices.isEmpty())
        {
            throw new RuntimeException("no choices in response");
        }
        return processChoice(choices.getFirst());
    }


    private Reply processChoice(ChatCompletion.Choice choice)
    {
        String modelReply = choice.message().content().orElse("Reply is empty");
        if(choice.message().toolCalls().isEmpty() || choice.message().toolCalls().get().isEmpty())
        {
            return Reply.stop(modelReply);
        }
        logger.debug("Model reply: " + modelReply + "\n");
        List<ChatCompletionMessageToolCall> toolCalls = choice.message().toolCalls().get();
        this.contextBuilder.addMessage(MessageFactory.assistant(toolCalls));
        for(ChatCompletionMessageToolCall toolCall : toolCalls)
        {
            callTool(toolCall);
        }
        return Reply.keepGoing();
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


    private void callTool(ChatCompletionMessageToolCall toolCall)
    {
        ChatCompletionMessageFunctionToolCall.Function fn = toolCall.asFunction().function();
        String result = executeTool(fn.name(), fn.arguments());
        logger.debug("Tool [" + fn.name() + "] output: <<\n\n" + result + "\n\n>>");
        this.contextBuilder.addMessage(MessageFactory.tool(toolCall.asFunction().id(), result));
    }


    static class Reply
    {
        public boolean shouldStop;
        public String answer;


        static Reply stop(String answer)
        {
            var reply = new Reply();
            reply.shouldStop = true;
            reply.answer = answer;
            return reply;
        }


        static Reply keepGoing()
        {
            var reply = new Reply();
            reply.shouldStop = false;
            return reply;
        }
    }
}
