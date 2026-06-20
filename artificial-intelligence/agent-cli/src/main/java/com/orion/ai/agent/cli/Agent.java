package com.orion.ai.agent.cli;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import java.util.List;

public class Agent
{
    private static final int MAX_ITERATIONS = 5;
    private static final Logger logger = new Logger();
    private final OpenAIClient client;
    private final ChatCompletionCreateParams.Builder contextBuilder;


    public Agent(String apiKey, String baseUrl)
    {
        this.client = OpenAIOkHttpClient.builder()
                                        .apiKey(apiKey)
                                        .baseUrl(baseUrl)
                                        .build();
        this.contextBuilder = ChatCompletionCreateParams.builder()
                                                        .model("nvidia/nemotron-3-ultra-550b-a55b:free");
        for(Class<?> tool : ToolsRegistry.getAll())
        {
            contextBuilder.addTool(tool);
        }
    }


    public String prompt(String prompt)
    {
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


    private Reply reason()
    {
        ChatCompletion response = client.chat().completions().create(this.contextBuilder.build());
        if(response.choices().isEmpty())
        {
            throw new RuntimeException("no choices in response");
        }
        return processChoice(response.choices().getFirst());
    }


    private Reply processChoice(ChatCompletion.Choice choice)
    {
        String modelReply = choice.message().content().orElse("Reply is empty");
        if(choice.message().toolCalls().isEmpty())
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


    private void callTool(ChatCompletionMessageToolCall toolCall)
    {
        ChatCompletionMessageFunctionToolCall.Function fn = toolCall.asFunction().function();
        String result = fn.arguments(ToolsRegistry.get(fn.name())).execute();
        logger.debug("Tool [" + toolCall.asFunction().function().name() + "] output: <<\n\n" + result + "\n\n>>");
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
