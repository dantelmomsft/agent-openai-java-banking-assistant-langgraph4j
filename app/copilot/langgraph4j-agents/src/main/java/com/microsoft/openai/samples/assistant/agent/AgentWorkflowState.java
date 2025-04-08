// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.AppenderChannel;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;
import java.util.function.Supplier;

public class AgentWorkflowState extends MessagesState<ChatMessage>  {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "messages", new LC4JAppenderChannel(ArrayList::new)
    );

    // Required by Jackson Serialization
    public AgentWorkflowState() {
        super( Map.of() );
    }

    public AgentWorkflowState(Map<String, Object> initData ) {
        super( initData );
    }

    /**
     * conversation memory
     * @return list of in memory messages
     */
    public List<ChatMessage> memory() {
        return this.<List<ChatMessage>>value("memory").orElseGet(List::of);
    }

}

class LC4JAppenderChannel extends AppenderChannel<ChatMessage> {

    /**
     * Constructs a new instance of {@code AppenderChannel} with the specified default provider.
     *
     * @param defaultProvider a supplier for the default list that will be used when no other list is available
     */
    protected LC4JAppenderChannel(Supplier<List<ChatMessage>> defaultProvider) {
        super( new ReducerDisallowDuplicate<>(),  defaultProvider);
    }

    @Override
    protected List<ChatMessage> validateNewValues(List<?> list) {
        var result = list.stream().map( v ->
            ( v instanceof String text ) ? UserMessage.from( text ) : v )
            .toList();
        return super.validateNewValues(result);
    }
}