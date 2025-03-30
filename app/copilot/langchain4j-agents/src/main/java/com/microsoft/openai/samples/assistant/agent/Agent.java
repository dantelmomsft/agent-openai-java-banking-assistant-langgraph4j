package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;

public interface Agent {

    String getName();
    AgentMetadata getMetadata();
    void invoke(List<ChatMessage> chatHistory) throws AgentExecutionException;
}
