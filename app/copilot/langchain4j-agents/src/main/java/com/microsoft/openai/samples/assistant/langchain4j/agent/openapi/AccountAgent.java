package com.microsoft.openai.samples.assistant.langchain4j.agent.openapi;

import com.microsoft.langchain4j.agent.AgentMetadata;
import com.microsoft.langchain4j.agent.openapi.OpenAPIImporterMetadata;
import com.microsoft.langchain4j.agent.openapi.OpenAPIToolAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;

import java.util.List;
import java.util.Map;

public class AccountAgent extends OpenAPIToolAgent {

    private final Prompt agentPrompt;

    private static final String ACCOUNT_AGENT_SYSTEM_MESSAGE = """
         you are a personal financial advisor who help the user to retrieve information about their bank accounts.
         Use html list or table to display the account information.
         Always use the below logged user details to retrieve account info:
         '{{loggedUserName}}'
        """;

    public AccountAgent(ChatLanguageModel chatModel, String loggedUserName, String restServerUrl) {
        super(chatModel, List.of(new OpenAPIImporterMetadata("account-api", "account.yaml", restServerUrl)));

        if (loggedUserName == null || loggedUserName.isEmpty()) {
            throw new IllegalArgumentException("loggedUserName cannot be null or empty");
        }

        PromptTemplate promptTemplate = PromptTemplate.from(ACCOUNT_AGENT_SYSTEM_MESSAGE);
        this.agentPrompt = promptTemplate.apply(Map.of("loggedUserName", loggedUserName));
    }

    @Override
    public String getName() {
        return "AccountAgent";
    }

    @Override
    public AgentMetadata getMetadata() {
        return new AgentMetadata(
            "Personal financial advisor for retrieving bank account information.",
            List.of("RetrieveAccountInfo", "DisplayAccountDetails")
        );
    }

    @Override
    protected String getSystemMessage() {
        return agentPrompt.text();
    }

}
