package com.microsoft.openai.samples.assistant.agent;

import com.microsoft.openai.samples.assistant.langchain4j.agent.AccountAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.SupervisorAgent;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.JacksonMessagesStateSerializer;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.*;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class AgentWorkflowBuilder {

    public StateGraph<AgentWorkflowState> stateGraph() throws GraphStateException {

        final var modelThinking = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .responseFormat( ResponseFormat.JSON )
                .modelName("deepseek-r1:14b")
                .build();

        final var modelTools = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .responseFormat( ResponseFormat.JSON )
                .modelName("qwen2.5:7b")
                .build();



        var accountAgent = new AccountAgent( modelThinking,
                "bob.user@contoso.com",
                "http://localhost:8070" );

        var superVisorAgent = new SupervisorAgent( modelTools,
                    List.of( accountAgent )
                 );

        var serializer = new StateSerializer();

        AsyncNodeAction<AgentWorkflowState> userProxy = node_async(state ->
            // remove intent from state
            new HashMap<>() {{ put("intent", null ); }}
        );

        AsyncEdgeAction<AgentWorkflowState> superVisorRoute =  edge_async((state ) ->
                state.intent().orElseGet( () ->
                        state.clarification().map( c -> Intent.User.name()).orElse(END) ));


        return new StateGraph<>( AgentWorkflowState.SCHEMA, serializer )
                .addNode( "Supervisor", SupervisorAgentNode.of( superVisorAgent ) )
                .addNode( Intent.User.name(), userProxy )
                .addNode( Intent.AccountInfo.name(), AccountAgentNode.of( accountAgent ) )
                //.addNode( Intent.BillPayment.name(), PaymentAgentNode.of( modelThinking ) )
                //.addNode( Intent.TransactionHistory.name(), transactionAgent)
                //.addNode( Intent.RepeatTransaction.name(), transactionAgent )
                .addEdge(  START, "Supervisor" )
                .addConditionalEdges( "Supervisor",
                        superVisorRoute,
                        EdgeMappings.builder()
                                //.to( Intent.names() )
                                .to( Intent.AccountInfo.name() )
                                .to( Intent.User.name() )
                                .toEND()
                                .build())
                .addEdge( Intent.User.name(), "Supervisor" )
                .addEdge( Intent.AccountInfo.name(), "Supervisor" )
                //.addEdge( Intent.BillPayment.name(), "Supervisor" )
                //.addEdge( Intent.TransactionHistory.name(), "Supervisor"  )
                //.addEdge( Intent.RepeatTransaction.name(), "Supervisor" )
                ;
    }

    public CompiledGraph<AgentWorkflowState> build() throws GraphStateException {

        var graph = stateGraph();

        var checkPointSaver = new MemorySaver();

        var config = CompileConfig.builder()
                        .checkpointSaver( checkPointSaver )
                        .interruptBefore( Intent.User.name())
                        .build();

        return graph.compile(config);
    }
}



class StateSerializer extends JacksonMessagesStateSerializer<AgentWorkflowState> {

    public StateSerializer() {
        super( AgentWorkflowState::new );
    }
}

/*
class StateSerializer extends ObjectStreamStateSerializer<AgentWorkflowState> {

    public StateSerializer() {
        super( AgentWorkflowState::new );

        mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        mapper().register(ChatMessage.class, new ChatMesssageSerializer());
    }
}

*/

