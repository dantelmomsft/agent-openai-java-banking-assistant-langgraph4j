// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.openai.samples.assistant.langgraph4j;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Map;
import java.util.Optional;

public class AgentContext extends MessagesState<ChatMessage> {

    public AgentContext( Map<String, Object> initData ) {
        super( initData );
    }


    public Optional<String> result() {
        return value("result");
    }

}
