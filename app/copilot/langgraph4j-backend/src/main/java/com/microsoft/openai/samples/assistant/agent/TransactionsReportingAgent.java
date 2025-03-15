// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class TransactionsReportingAgent implements NodeAction<AgentWorkflowState> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionsReportingAgent.class);

    private String HISTORY_AGENT_SYSTEM_MESSAGE = """
    you are a personal financial advisor who help the user with their recurrent bill payments. To search about the payments history you need to know the payee name.
    If the user doesn't provide the payee name, search the last 10 transactions order by date.
    If the user want to search last transactions for a specific payee, ask to provide the payee name.
    Use html list or table to display the transaction information.
    Always use the below logged user details to search the transactions:
    %s
    Current timestamp: %s
    
    Before executing a function call, check data in below function calls cache:
     %s
    """;

    public static AsyncNodeAction<AgentWorkflowState> of(ChatLanguageModel model ) {
        return node_async( new TransactionsReportingAgent(model ));
    }

    private TransactionsReportingAgent( ChatLanguageModel model) {
    }


    @Override
    public Map<String, Object> apply(AgentWorkflowState agentContext) throws Exception {
        return Map.of();
    }
}


