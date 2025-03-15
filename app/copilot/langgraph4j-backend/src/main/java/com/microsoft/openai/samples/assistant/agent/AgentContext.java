// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.AppenderChannel;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;

import static java.util.Collections.unmodifiableMap;

public class AgentContext extends MessagesState<ChatMessage>  {

    public static final Map<String, Channel<?>> SCHEMA = unmodifiableMap( new HashMap<>() {{
            putAll( MessagesState.SCHEMA ); // inherit schema
            put("memory", AppenderChannel.of(ArrayList::new));
        }}
    );

    public AgentContext( Map<String, Object> initData ) {
        super( initData );
    }

    public Optional<String> intent() {
        return value("intent");
    }

    public Optional<String> clarification() {
        return value("clarification");
    }

    /**
     * conversation memory
     * @return list of in memory messages
     */
    public List<ChatMessage> memory() {
        return this.<List<ChatMessage>>value("memory").orElseGet(List::of);
    }


}
