package com.microsoft.openai.samples.assistant.agent;

import com.microsoft.openai.samples.assistant.langchain4j.agent.AccountAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.AccountMCPAgent;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public  class AccountAgentNode implements NodeAction<AgentWorkflowState> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountAgentNode.class);

    private final AccountMCPAgent agent;

    public static AsyncNodeAction<AgentWorkflowState> of( AccountMCPAgent agent ) {
        return node_async( new AccountAgentNode( agent ));
    }

    public AccountAgentNode( AccountMCPAgent agent ) {
        this.agent = Objects.requireNonNull( agent, "agent cannot be null");
    }

    @Override
    public Map<String, Object> apply(AgentWorkflowState state) throws Exception {

        var messages = agent.invoke( state.messages() );

        return Map.of( "messages", messages, // result from reasoning
                "intent", Intent.User.name() // force intent
        );
    }
}
