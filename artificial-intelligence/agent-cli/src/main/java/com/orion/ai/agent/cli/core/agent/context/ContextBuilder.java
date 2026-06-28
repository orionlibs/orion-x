package com.orion.ai.agent.cli.core.agent.context;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import java.util.List;

public class ContextBuilder
{
    private final ChatCompletionCreateParams.Builder delegate;


    public ContextBuilder(String modelId)
    {
        delegate = ChatCompletionCreateParams.builder().model(modelId);
    }


    public ContextBuilder addMessage(ChatCompletionUserMessageParam message)
    {
        delegate.addMessage(message);
        return this;
    }


    public ContextBuilder addMessage(ChatCompletionAssistantMessageParam message)
    {
        delegate.addMessage(message);
        return this;
    }


    public ContextBuilder addMessage(ChatCompletionToolMessageParam message)
    {
        delegate.addMessage(message);
        return this;
    }


    public ContextBuilder addTool(Class<?> tool)
    {
        delegate.addTool(tool);
        return this;
    }


    public List<ChatCompletionMessageParam> getMessages()
    {
        return delegate.build().messages();
    }


    public ChatCompletionCreateParams build()
    {
        return delegate.build();
    }
}
