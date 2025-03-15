package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.RemoveByHash;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AgentMemory implements ChatMemory {

    private AgentWorkflowState state;

    public AgentMemory() {
    }

    void setState( AgentWorkflowState state ) {
        this.state = state ;
    }

    @Override
    public Object id() {
        return "state";
    }

    @Override
    public void add(ChatMessage message) {
        Objects.requireNonNull(state);
        if( message instanceof SystemMessage systemMessage ) {
            var prevSystemMessage = state.memory().stream()
                    .filter(m -> m instanceof SystemMessage)
                    .map(SystemMessage.class::cast)
                    .findAny()
                    ;
            if( prevSystemMessage.isPresent() &&  !prevSystemMessage.get().equals(systemMessage) ) {

                // Replace System Message in memory
                AgentState.updateState(state,
                        Map.of("memory", List.of(RemoveByHash.of(prevSystemMessage.get()), message)),
                        AgentWorkflowState.SCHEMA);
                return;
            }
        }
        // Add System Message in memory
        AgentState.updateState( state, Map.of( "memory", message ), AgentWorkflowState.SCHEMA );

    }

    @Override
    public List<ChatMessage> messages() {
        Objects.requireNonNull(state);
        return state.memory();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("memory clear is not implemented yet!");
    }
}
