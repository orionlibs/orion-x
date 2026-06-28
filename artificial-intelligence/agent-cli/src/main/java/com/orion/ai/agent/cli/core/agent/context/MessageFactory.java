package com.orion.ai.agent.cli.core.agent.context;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import java.util.List;

public class MessageFactory
{
    public static ChatCompletionUserMessageParam user(String prompt)
    {
        return ChatCompletionUserMessageParam.builder()
                                             .content(prompt)
                                             .build();
    }


    public static ChatCompletionAssistantMessageParam assistant(List<ChatCompletionMessageToolCall> toolCalls)
    {
        return ChatCompletionAssistantMessageParam.builder()
                                                  .toolCalls(toolCalls)
                                                  .build();
    }


    public static ChatCompletionToolMessageParam tool(String toolCallID, String callResult)
    {
        return ChatCompletionToolMessageParam.builder()
                                             .toolCallId(toolCallID)
                                             .content(callResult)
                                             .build();
    }
}
