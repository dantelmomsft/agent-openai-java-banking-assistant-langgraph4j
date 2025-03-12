package com.microsoft.openai.samples.assistant.langgraph4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

public class AgentWorkflowBuilder {

    public CompiledGraph<AgentContext> build() throws GraphStateException {

        var serializer = new StateSerializer();

        var graph = new StateGraph<>( AgentContext.SCHEMA, serializer );


        return graph.compile();
    }
}


class StateSerializer extends ObjectStreamStateSerializer<AgentContext> {

    public StateSerializer() {
        super(AgentContext::new);

        mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        mapper().register(ChatMessage.class, new ChatMesssageSerializer());
    }
}

class SupervisorAgent implements AsyncNodeAction<AgentContext> {


    static class Router {
        @Description("Worker to route to next. If no workers needed, route to FINISH.")
        String next;

        @Override
        public String toString() {
            return format( "Router[next: %s]",next);
        }
    }

    interface Service {
        @SystemMessage( """
                    You are a supervisor tasked with managing a conversation between the following workers: {{members}}.
                    Given the following user request, respond with the worker to act next.
                    Each worker will perform a task and respond with their results and status.
                    When finished, respond with FINISH.
                    """)
        Router evaluate(@V("members") String members, @dev.langchain4j.service.UserMessage  String userMessage);
    }

    final Service service;
    public final String[] members = {"researcher", "coder" };

    public SupervisorAgent(ChatLanguageModel model ) {

        service = AiServices.create( Service.class, model );
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(AgentContext state) {
        var m = String.join(",", members);
        var message = state.lastMessage().orElseThrow();
        var text = switch( message.type() ) {
            case USER -> ((UserMessage)message).singleText();
            case AI -> ((AiMessage)message).text();
            default -> throw new IllegalStateException("unexpected message type: " + message.type() );
        };

        var result = service.evaluate( m, text );
        return CompletableFuture.completedFuture(Map.of( "next", result.next ));
    }
}

