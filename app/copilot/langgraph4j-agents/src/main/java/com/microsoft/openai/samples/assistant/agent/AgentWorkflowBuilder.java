package com.microsoft.openai.samples.assistant.agent;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.identity.AzureCliCredentialBuilder;
import com.microsoft.openai.samples.assistant.invoice.DocumentIntelligenceInvoiceScanHelper;
import com.microsoft.openai.samples.assistant.langchain4j.agent.PaymentAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.SupervisorAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.AccountMCPAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.PaymentMCPAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.TransactionHistoryMCPAgent;
import com.microsoft.openai.samples.assistant.proxy.BlobStorageProxy;
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

    public DocumentIntelligenceInvoiceScanHelper documentInvoiceScanner() {
        var  storageAccountService = "https://%s.blob.core.windows.net".formatted(System.getenv("AZURE_STORAGE_ACCOUNT"));
        var containerName = "content";

        return new DocumentIntelligenceInvoiceScanHelper(
                        new DocumentIntelligenceClientBuilder()
                                .endpoint("https://endpoint.cognitiveservices.azure.com")
                                .buildClient(),
                        new BlobStorageProxy( storageAccountService, containerName,
                                new AzureCliCredentialBuilder().build() ) );

    }

    public StateGraph<AgentWorkflowState> graph() throws GraphStateException {

        var model = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

        var accountUserName = "bob.user@contoso.com";
        var paymentMCPServerUrl = "http://localhost:8060/sse";
        var accountMCPServerUrl = "http://localhost:8070/sse";
        var transactionMCPServerUrl = "http://localhost:8090/sse";

        var accountAgent = new AccountMCPAgent( model, accountUserName, accountMCPServerUrl );

        var transactionAgent = new TransactionHistoryMCPAgent( model, accountUserName, transactionMCPServerUrl, accountMCPServerUrl );

        var paymentAgent = new PaymentMCPAgent(model,
                documentInvoiceScanner(),
                accountUserName,
                transactionMCPServerUrl,
                accountMCPServerUrl,
                paymentMCPServerUrl);

        var superVisorAgent = new SupervisorAgent( model, List.of( accountAgent, transactionAgent, paymentAgent ) );

        AsyncEdgeAction<AgentWorkflowState> superVisorRoute =  edge_async((state ) -> {

            var intent = state.lastMessage()
                    .map( AiMessage.class::cast )
                    .map( AiMessage::text )
                    .orElseThrow();

            return Intent.names().stream()
                    .filter( i -> Objects.equals(i,intent ) )
                    .findFirst()
                    .orElse( "end" );
        });

        var serializer = new LC4jJacksonStateSerializer<>( AgentWorkflowState::new );

        return new StateGraph<>( AgentWorkflowState.SCHEMA, serializer )
                .addNode( "Supervisor", SupervisorAgentNode.of( superVisorAgent ) )
                .addNode( Intent.AccountAgent.name(), AgentNode.of( accountAgent ) )
                .addNode( Intent.TransactionHistoryAgent.name(), AgentNode.of(  transactionAgent ) )
                .addNode( Intent.PaymentAgent.name(), AgentNode.of( paymentAgent ) )
                .addEdge(  START, "Supervisor" )
                .addConditionalEdges( "Supervisor",
                        superVisorRoute,
                        EdgeMappings.builder()
                                .to( Intent.names() )
                                .toEND("end")
                                .build())
                .addEdge( Intent.AccountAgent.name(), "Supervisor" )
                .addEdge( Intent.TransactionHistoryAgent.name(), "Supervisor"  )
                .addEdge( Intent.PaymentAgent.name(), "Supervisor" )
                ;
    }

    public CompiledGraph<AgentWorkflowState> build() throws GraphStateException {

        var graph = graph();

        var checkPointSaver = new MemorySaver();

        var config = CompileConfig.builder()
                        .checkpointSaver( checkPointSaver )
                        .build();

        return graph.compile(config);
    }
}


