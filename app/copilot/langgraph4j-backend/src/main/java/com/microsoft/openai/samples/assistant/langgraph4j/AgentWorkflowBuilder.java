package com.microsoft.openai.samples.assistant.langgraph4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

import java.util.*;

import static java.lang.String.format;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class AgentWorkflowBuilder {


    private final EdgeAction<AgentContext> superVisorRoute =  (state ) ->
        state.intent().orElseGet( () ->
                state.clarification().map( c -> SupervisorAgent.UserProxy).orElse(END) )
     ;

    public CompiledGraph<AgentContext> build() throws GraphStateException {

        final var modelThinking = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .responseFormat( ResponseFormat.JSON )
                .modelName("deepseek-r1:14b")
                .build();

        var serializer = new StateSerializer();

        var graph = new StateGraph<>( AgentContext.SCHEMA, serializer )
                .addNode( "Supervisor", SupervisorAgent.of(modelThinking) )
                .addNode( SupervisorAgent.UserProxy, node_async(state -> Map.of() ) )
                .addNode( SupervisorAgent.Intent.AccountInfo.name(), node_async( state -> Map.of() ) )
                .addNode( SupervisorAgent.Intent.BillPayment.name(), node_async( state -> Map.of() ) )
                .addNode( SupervisorAgent.Intent.TransactionHistory.name(), node_async( state -> Map.of() ) )
                .addNode( SupervisorAgent.Intent.RepeatTransaction.name(), node_async( state -> Map.of() ) )
                .addEdge(  START, "Supervisor" )
                .addConditionalEdges( "Supervisor",
                        edge_async(superVisorRoute),
                        EdgeMappings.builder()
                                .to( SupervisorAgent.Intent.names() )
                                .to( SupervisorAgent.UserProxy)
                                .toEND()
                                .build())
                .addEdge( SupervisorAgent.UserProxy, "Supervisor" )
                .addEdge( SupervisorAgent.Intent.AccountInfo.name(), "Supervisor" )
                .addEdge( SupervisorAgent.Intent.BillPayment.name(), "Supervisor" )
                .addEdge( SupervisorAgent.Intent.TransactionHistory.name(), "Supervisor"  )
                .addEdge( SupervisorAgent.Intent.RepeatTransaction.name(), "Supervisor" )
                ;

        var checkPointSaver = new MemorySaver();

        var config = CompileConfig.builder()
                        .checkpointSaver( checkPointSaver )
                        .interruptBefore( SupervisorAgent.UserProxy)
                        .build();

        return graph.compile(config);
    }
}


class StateSerializer extends ObjectStreamStateSerializer<AgentContext> {

    public StateSerializer() {
        super(AgentContext::new);

        mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        mapper().register(ChatMessage.class, new ChatMesssageSerializer());
    }
}

class EdgeMappings {

    public static class Builder {

        private final Map<String, String> mappings = new HashMap<>();

        public Builder toEND() {
            mappings.put(END, END);
            return this;
        }

        public Builder toEND( String label ) {
            mappings.put(label, END);
            return this;
        }

        public Builder to( String destination ) {
            mappings.put(destination, destination);
            return this;
        }

        public Builder to( String label, String destination ) {
            mappings.put(label, destination);
            return this;
        }

        public Builder to( List<String> destinations ) {
            destinations.forEach(this::to);
            return this;
        }

        public Builder to( String[] destinations ) {
            return to( Arrays.asList(destinations) );
        }

        public Map<String, String> build() {
            return Collections.unmodifiableMap(mappings);
        }
    }

    public static Builder builder() { return new Builder(); }
}