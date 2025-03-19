package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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

        // var  memory = MessageWindowChatMemory.withMaxMessages(10);
        var memory = MessageWindowChatMemory.withMaxMessages(10);

        var serializer = new StateSerializer();

        AsyncNodeAction<AgentWorkflowState> transactionAgent = TransactionsReportingAgent.of( modelThinking, memory );

        AsyncNodeAction<AgentWorkflowState> userProxy = node_async(state ->
            // remove intent from state
            new HashMap<>() {{ put("intent", null ); }}
        );

        AsyncEdgeAction<AgentWorkflowState> superVisorRoute =  edge_async((state ) ->
                state.intent().orElseGet( () ->
                        state.clarification().map( c -> SupervisorAgent.Intent.User.name()).orElse(END) ));

        return new StateGraph<>( AgentWorkflowState.SCHEMA, serializer )
                .addNode( "Supervisor", SupervisorAgent.of(modelThinking, memory) )
                .addNode( SupervisorAgent.Intent.User.name(), userProxy )
                .addNode( SupervisorAgent.Intent.AccountInfo.name(), AccountAgent.of( modelThinking, memory ) )
                .addNode( SupervisorAgent.Intent.BillPayment.name(), PaymentAgent.of( modelThinking, memory ) )
                .addNode( SupervisorAgent.Intent.TransactionHistory.name(), transactionAgent)
                .addNode( SupervisorAgent.Intent.RepeatTransaction.name(), transactionAgent )
                .addEdge(  START, "Supervisor" )
                .addConditionalEdges( "Supervisor",
                        superVisorRoute,
                        EdgeMappings.builder()
                                .to( SupervisorAgent.Intent.names() )
                                //.toEND()
                                .build())
                .addEdge( SupervisorAgent.Intent.User.name(), "Supervisor" )
                .addEdge( SupervisorAgent.Intent.AccountInfo.name(), "Supervisor" )
                .addEdge( SupervisorAgent.Intent.BillPayment.name(), "Supervisor" )
                .addEdge( SupervisorAgent.Intent.TransactionHistory.name(), "Supervisor"  )
                .addEdge( SupervisorAgent.Intent.RepeatTransaction.name(), "Supervisor" )
                ;
    }

    public CompiledGraph<AgentWorkflowState> build() throws GraphStateException {

        var graph = stateGraph();

        var checkPointSaver = new MemorySaver();

        var config = CompileConfig.builder()
                        .checkpointSaver( checkPointSaver )
                        .interruptBefore( SupervisorAgent.Intent.User.name())
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

