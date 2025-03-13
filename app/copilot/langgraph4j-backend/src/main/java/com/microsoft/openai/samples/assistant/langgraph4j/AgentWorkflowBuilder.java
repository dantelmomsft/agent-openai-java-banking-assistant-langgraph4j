package com.microsoft.openai.samples.assistant.langgraph4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class AgentWorkflowBuilder {


    private final EdgeAction<AgentContext> superVisorRoute =  (state ) ->
        state.intent().orElseGet( () ->
                state.clarification().map( c -> SupervisorAgent.Clarification ).orElse(END) )
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
                .addNode( SupervisorAgent.Clarification, node_async( state -> Map.of() ) )
                .addNode( SupervisorAgent.Intent.AccountInfo.name(), node_async( state -> Map.of() ) )
                .addNode( SupervisorAgent.Intent.BillPayment.name(), node_async( state -> Map.of() ) )
                .addNode( SupervisorAgent.Intent.TransactionHistory.name(), node_async( state -> Map.of() ) )
                .addNode( SupervisorAgent.Intent.RepeatTransaction.name(), node_async( state -> Map.of() ) )
                .addEdge(  START, "Supervisor" )
                .addConditionalEdges( "Supervisor",
                        edge_async(superVisorRoute),
                        EdgeMappings.builder()
                                .to( SupervisorAgent.Intent.names() )
                                .to( SupervisorAgent.Clarification )
                                .toEND()
                                .build())
                .addEdge( SupervisorAgent.Clarification, "Supervisor" )
                .addEdge( SupervisorAgent.Intent.AccountInfo.name(), END )
                .addEdge( SupervisorAgent.Intent.BillPayment.name(), END )
                .addEdge( SupervisorAgent.Intent.TransactionHistory.name(), END  )
                .addEdge( SupervisorAgent.Intent.RepeatTransaction.name(), END )
                ;

        var checkPointSaver = new MemorySaver();

        var config = CompileConfig.builder()
                        .checkpointSaver( checkPointSaver )
                        .interruptBefore( SupervisorAgent.Clarification )
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