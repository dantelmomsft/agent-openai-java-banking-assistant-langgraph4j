package com.microsoft.openai.samples.assistant.agent;

import com.microsoft.openai.samples.assistant.langchain4j.agent.SupervisorAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.AccountMCPAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.bsc.langgraph4j.state.RemoveByHash;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.*;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class AgentWorkflowBuilder {

    public StateGraph<AgentWorkflowState> graph() throws GraphStateException {

        /*
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
        */

        var model = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

        var accountAgent = new AccountMCPAgent( model,
                "bob.user@contoso.com",
                "http://localhost:8070/sse" );

        var superVisorAgent = new SupervisorAgent( model, List.of( accountAgent ) );

        var serializer = new LC4jJacksonStateSerializer<>( AgentWorkflowState::new );

        AsyncNodeAction<AgentWorkflowState> userProxy = node_async(state -> {
            // remove last message (ie the  intent ) from state
            var lastMessage = state.lastMessage().orElseThrow();
            return Map.of("messages", RemoveByHash.of(lastMessage) );
        });

        AsyncEdgeAction<AgentWorkflowState> superVisorRoute =  edge_async((state ) -> {

            var intent = state.lastMessage()
                    .map( AiMessage.class::cast )
                    .map( AiMessage::text )
                    .orElseThrow();

            return Intent.names().stream()
                    .filter( i -> Objects.equals(i,intent ) )
                    .findFirst()
                    .orElse( Intent.User.name() );
        });


        return new StateGraph<>( AgentWorkflowState.SCHEMA, serializer )
                .addNode( "Supervisor", SupervisorAgentNode.of( superVisorAgent ) )
                .addNode( Intent.User.name(), userProxy )
                .addNode( Intent.AccountAgent.name(), AgentNode.of( accountAgent ) )
                //.addNode( Intent.BillPayment.name(), PaymentAgentNode.of( modelThinking ) )
                //.addNode( Intent.TransactionHistory.name(), transactionAgent)
                //.addNode( Intent.RepeatTransaction.name(), transactionAgent )
                .addEdge(  START, "Supervisor" )
                .addConditionalEdges( "Supervisor",
                        superVisorRoute,
                        EdgeMappings.builder()
                                //.to( Intent.names() )
                                .to( Intent.AccountAgent.name() )
                                .to( Intent.User.name() )
                                .toEND()
                                .build())
                .addEdge( Intent.User.name(), "Supervisor" )
                .addEdge( Intent.AccountAgent.name(), "Supervisor" )
                //.addEdge( Intent.BillPayment.name(), "Supervisor" )
                //.addEdge( Intent.TransactionHistory.name(), "Supervisor"  )
                //.addEdge( Intent.RepeatTransaction.name(), "Supervisor" )
                ;
    }

    public CompiledGraph<AgentWorkflowState> build() throws GraphStateException {

        var graph = graph();

        var checkPointSaver = new MemorySaver();

        var config = CompileConfig.builder()
                        .checkpointSaver( checkPointSaver )
                        .interruptBefore( Intent.User.name())
                        .build();

        return graph.compile(config);
    }
}


