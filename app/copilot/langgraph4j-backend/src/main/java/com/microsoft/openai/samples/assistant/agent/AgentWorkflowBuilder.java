package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.*;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class AgentWorkflowBuilder {

    public CompiledGraph<AgentWorkflowState> build() throws GraphStateException {

        final var modelThinking = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .responseFormat( ResponseFormat.JSON )
                .modelName("deepseek-r1:14b")
                .build();

        // var  memory = MessageWindowChatMemory.withMaxMessages(10);
        var memory = new AgentMemory();

        var serializer = new StateSerializer(memory);

        AsyncNodeAction<AgentWorkflowState> transactionAgent = TransactionsReportingAgent.of( modelThinking, memory );

        AsyncNodeAction<AgentWorkflowState> userProxy = node_async(state -> Map.of()  );

        AsyncEdgeAction<AgentWorkflowState> superVisorRoute =  edge_async((state ) ->
                state.intent().orElseGet( () ->
                        state.clarification().map( c -> SupervisorAgent.Intent.User.name()).orElse(END) ));

        var graph = new StateGraph<>( AgentWorkflowState.SCHEMA, serializer )
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

        var checkPointSaver = new MemorySaver();

        var config = CompileConfig.builder()
                        .checkpointSaver( checkPointSaver )
                        .interruptBefore( SupervisorAgent.Intent.User.name())
                        .build();

        return graph.compile(config);
    }
}


class StateSerializer extends ObjectStreamStateSerializer<AgentWorkflowState> {

    public StateSerializer( AgentMemory memory ) {
        super( (initialValue ) -> {
            var result = new AgentWorkflowState( initialValue );
            memory.setState( result );
            return result;
        });

        mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        mapper().register(ChatMessage.class, new ChatMesssageSerializer());
    }
}

