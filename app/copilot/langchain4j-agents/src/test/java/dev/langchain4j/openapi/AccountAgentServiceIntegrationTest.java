package dev.langchain4j.openapi;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountAgentServiceIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountAgentServiceIntegrationTest.class);

    private static final String ACCOUNT_AGENT_SYSTEM_MESSAGE = """
         you are a personal financial advisor who help the user to retrieve information about their bank accounts.
         Use html list or table to display the account information.
         Always use the below logged user details to retrieve account info:
         '{{loggedUserName}}'
        """;

    interface AccountAgent {

        @SystemMessage( ACCOUNT_AGENT_SYSTEM_MESSAGE )
        String invoke( @V("loggedUserName") String loggedUserName,  @UserMessage String userMessage);
    }

    public static void main(String[] args) throws Exception {

        //Azure Open AI Chat Model
        var model = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

        var loggedUserName = "bob.user@contoso.com";
        var restServerUrl = "http://localhost:8080";


        var chatMemory = MessageWindowChatMemory.builder()
                .id("AccountAgent-01")
                .maxMessages(20)
                .build();

        var apiImporter = OpenAPIToolsImporter.builder()
                .withToolName("account-api")
                .withSpecPath("account.yaml")
                .withServerUrl(restServerUrl)
                .build();

        var accountAgent = AiServices.builder(AccountAgent.class)
                            .chatMemory( chatMemory )
                            .chatLanguageModel( model )
                            .tools( apiImporter.getSpecificationsMaps())
                            .build();

        var response = accountAgent.invoke( loggedUserName,"How much money do I have in my account?" );
        LOGGER.info( "\n1st response: {}", response);

        response = accountAgent.invoke(loggedUserName,"what about my visa");
        LOGGER.info( "\n2nd response: {}", response);
    }


}
