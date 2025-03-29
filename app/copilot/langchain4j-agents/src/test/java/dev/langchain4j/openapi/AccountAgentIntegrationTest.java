package dev.langchain4j.openapi;

import com.microsoft.openai.samples.assistant.agent.AccountAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;

public class AccountAgentIntegrationTest {

    public static void main(String[] args) throws Exception {

        //Azure Open AI Chat Model
        AzureOpenAiChatModel azureOpenAiChatModel = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

        AccountAgent accountAgent = new AccountAgent(azureOpenAiChatModel,"bob.user@contoso.com","http://localhost:8080");

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id("12345")
                .maxMessages(20)
                .build();

        chatMemory.add(UserMessage.from("How much money do I have in my account?"));
        accountAgent.invoke(chatMemory);
        AiMessage response = (AiMessage) chatMemory.messages().get(chatMemory.messages().size()-1);
        System.out.println(response.text());

        chatMemory.add(UserMessage.from("what about my visa"));
        accountAgent.invoke(chatMemory);
        response = (AiMessage) chatMemory.messages().get(chatMemory.messages().size()-1);
        System.out.println(response.text());
    }
}
