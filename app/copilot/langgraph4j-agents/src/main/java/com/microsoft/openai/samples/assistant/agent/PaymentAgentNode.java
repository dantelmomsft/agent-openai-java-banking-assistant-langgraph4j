package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class PaymentAgentNode implements NodeAction<AgentWorkflowState> {


    public static AsyncNodeAction<AgentWorkflowState> of(ChatLanguageModel model  ) {
        return node_async( new PaymentAgentNode(model ));
    }

    private PaymentAgentNode(ChatLanguageModel model) {

    }

    @Override
    public Map<String, Object> apply(AgentWorkflowState agentContext) throws Exception {
        return Map.of();
    }
}
