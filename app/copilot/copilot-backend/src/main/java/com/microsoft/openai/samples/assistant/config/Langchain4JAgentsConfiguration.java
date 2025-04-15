// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.openai.samples.assistant.config;

import com.microsoft.openai.samples.assistant.invoice.DocumentIntelligenceInvoiceScanHelper;
import com.microsoft.openai.samples.assistant.langchain4j.agent.SupervisorAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.AccountMCPAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.PaymentMCPAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.TransactionHistoryMCPAgent;
import com.microsoft.openai.samples.assistant.security.LoggedUserService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "agent.strategy", havingValue = "langchain4j", matchIfMissing = true)
public class Langchain4JAgentsConfiguration {
    @Value("${transactions.api.url}") String transactionsMCPServerUrl;
    @Value("${accounts.api.url}") String accountsMCPServerUrl;
    @Value("${payments.api.url}") String paymentsMCPServerUrl;

    private final ChatLanguageModel chatLanguageModel;
    private final LoggedUserService loggedUserService;
    private final DocumentIntelligenceInvoiceScanHelper documentIntelligenceInvoiceScanHelper;

    public Langchain4JAgentsConfiguration(ChatLanguageModel chatLanguageModel, LoggedUserService loggedUserService, DocumentIntelligenceInvoiceScanHelper documentIntelligenceInvoiceScanHelper) {
        this.chatLanguageModel = chatLanguageModel;
        this.loggedUserService = loggedUserService;
        this.documentIntelligenceInvoiceScanHelper = documentIntelligenceInvoiceScanHelper;
    }
    @Bean
    public AccountMCPAgent accountMCPAgent() {
        return new AccountMCPAgent(chatLanguageModel, loggedUserService.getLoggedUser().username(), accountsMCPServerUrl);
    }

    @Bean
    public TransactionHistoryMCPAgent transactionHistoryMCPAgent() {
        return new TransactionHistoryMCPAgent(chatLanguageModel, loggedUserService.getLoggedUser().username(), transactionsMCPServerUrl,accountsMCPServerUrl);
    }

    @Bean
    public PaymentMCPAgent paymentMCPAgent() {
        return new PaymentMCPAgent(chatLanguageModel,documentIntelligenceInvoiceScanHelper, loggedUserService.getLoggedUser().username(),transactionsMCPServerUrl,accountsMCPServerUrl, paymentsMCPServerUrl);
    }

    @Bean
    public SupervisorAgent supervisorAgent(ChatLanguageModel chatLanguageModel){
        return new SupervisorAgent(chatLanguageModel,
                List.of(accountMCPAgent(),
                        transactionHistoryMCPAgent(),
                        paymentMCPAgent()));

    }

}
