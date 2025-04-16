// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.openai.samples.assistant.config;

import com.microsoft.openai.samples.assistant.agent.AgentNode;
import com.microsoft.openai.samples.assistant.agent.AgentWorkflowState;
import com.microsoft.openai.samples.assistant.agent.Intent;
import com.microsoft.openai.samples.assistant.agent.SupervisorAgentNode;
import com.microsoft.openai.samples.assistant.invoice.DocumentIntelligenceInvoiceScanHelper;
import com.microsoft.openai.samples.assistant.langchain4j.agent.SupervisorAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.AccountMCPAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.PaymentMCPAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.TransactionHistoryMCPAgent;
import com.microsoft.openai.samples.assistant.security.LoggedUserService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Objects;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Configuration
@ConditionalOnProperty(name = "agent.strategy", havingValue = "langgraph4j")
public class Langgraph4JAgentsConfiguration {
    @Value("${transactions.api.url}") String transactionsMCPServerUrl;
    @Value("${accounts.api.url}") String accountsMCPServerUrl;
    @Value("${payments.api.url}") String paymentsMCPServerUrl;


    private final ChatLanguageModel chatLanguageModel;
    private final LoggedUserService loggedUserService;
    private final DocumentIntelligenceInvoiceScanHelper documentIntelligenceInvoiceScanHelper;

    public Langgraph4JAgentsConfiguration(ChatLanguageModel chatLanguageModel, LoggedUserService loggedUserService, DocumentIntelligenceInvoiceScanHelper documentIntelligenceInvoiceScanHelper) {
        this.chatLanguageModel = chatLanguageModel;
        this.loggedUserService = loggedUserService;
        this.documentIntelligenceInvoiceScanHelper = documentIntelligenceInvoiceScanHelper;
    }


    @Bean
    public CompiledGraph<AgentWorkflowState> langgraph4jWorkflow() throws GraphStateException {
        var accountAgent = new AccountMCPAgent(chatLanguageModel, loggedUserService.getLoggedUser().username(), accountsMCPServerUrl);
        var transactionHistoryAgent = new TransactionHistoryMCPAgent(chatLanguageModel, loggedUserService.getLoggedUser().username(), transactionsMCPServerUrl, accountsMCPServerUrl);
        var paymentAgent = new PaymentMCPAgent(chatLanguageModel, documentIntelligenceInvoiceScanHelper, loggedUserService.getLoggedUser().username(), transactionsMCPServerUrl, accountsMCPServerUrl, paymentsMCPServerUrl);

        var supervisorAgent = new SupervisorAgent(chatLanguageModel,
                List.of(accountAgent,
                        transactionHistoryAgent,
                        paymentAgent));

        AsyncEdgeAction<AgentWorkflowState> supervisorRoute =  edge_async((state ) -> {

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

        var graph = new StateGraph<>( AgentWorkflowState.SCHEMA, serializer )
                .addNode( "Supervisor", SupervisorAgentNode.of( supervisorAgent ) )
                .addNode( Intent.AccountAgent.name(), AgentNode.of( accountAgent ) )
                .addNode( Intent.TransactionHistoryAgent.name(), AgentNode.of(  transactionHistoryAgent ) )
                .addNode( Intent.PaymentAgent.name(), AgentNode.of( paymentAgent ) )
                .addEdge(  START, "Supervisor" )
                .addConditionalEdges( "Supervisor",
                        supervisorRoute,
                        EdgeMappings.builder()
                                .to( Intent.names() )
                                .toEND("end")
                                .build())
                .addEdge( Intent.AccountAgent.name(), END )
                .addEdge( Intent.TransactionHistoryAgent.name(), END  )
                .addEdge( Intent.PaymentAgent.name(), END )
        ;

        // var checkPointSaver = new MemorySaver();

        var config = CompileConfig.builder()
                //.checkpointSaver( checkPointSaver )
                .build();

        return graph.compile(config);

    }

}
