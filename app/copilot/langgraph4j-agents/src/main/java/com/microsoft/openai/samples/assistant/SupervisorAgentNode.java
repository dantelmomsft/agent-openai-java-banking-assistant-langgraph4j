package com.microsoft.openai.samples.assistant;

import com.microsoft.openai.samples.assistant.langchain4j.agent.SupervisorAgent;
import dev.langchain4j.data.message.AiMessage;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;


/**
 * Implements methods for managing a financial advisor node that processes user intents.
 * The class provides functionality to evaluate user messages and determine the appropriate action based on pre-defined
 * intent patterns.
 */
public class SupervisorAgentNode implements NodeActionWithConfig<AgentWorkflowState> {

    final SupervisorAgent agent;

    public static AsyncNodeActionWithConfig<AgentWorkflowState> of(SupervisorAgent agent) {
        return node_async( new SupervisorAgentNode(agent) );
    }

    private SupervisorAgentNode(SupervisorAgent agent) {
        this.agent = Objects.requireNonNull( agent, "agent cannot be null");
    }

    @Override
    public Map<String, Object> apply(AgentWorkflowState state, RunnableConfig config) {

        var messages = agent.invoke(state.messages());

        if (messages.get(0) instanceof AiMessage nextAgentMessage) {
            return Map.of("nextAgent", nextAgentMessage.text());
        }

        throw new IllegalArgumentException("Invalid message type from Supervisor: " + messages.get(0).getClass().getName());
    }
}