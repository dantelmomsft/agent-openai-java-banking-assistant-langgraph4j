package com.microsoft.openai.samples.assistant.agent;

import org.bsc.langgraph4j.langchain4j.memory.GraphChatMemory;


public class AgentMemory extends GraphChatMemory<AgentWorkflowState> {

    private AgentWorkflowState state;

    public AgentMemory() {
        super( "memory", AgentWorkflowState.SCHEMA );
    }
}
