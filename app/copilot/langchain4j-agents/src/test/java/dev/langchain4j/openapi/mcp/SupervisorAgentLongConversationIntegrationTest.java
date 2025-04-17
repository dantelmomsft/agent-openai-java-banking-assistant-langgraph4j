package dev.langchain4j.openapi.mcp;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.identity.AzureCliCredentialBuilder;
import com.microsoft.openai.samples.assistant.invoice.DocumentIntelligenceInvoiceScanHelper;
import com.microsoft.openai.samples.assistant.langchain4j.agent.SupervisorRoutingAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.AccountMCPAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.PaymentMCPAgent;
import com.microsoft.openai.samples.assistant.langchain4j.agent.mcp.TransactionHistoryMCPAgent;
import com.microsoft.openai.samples.assistant.proxy.BlobStorageProxy;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;

import java.util.ArrayList;
import java.util.List;

public class SupervisorAgentLongConversationIntegrationTest {

    public static void main(String[] args) throws Exception {

        //Azure Open AI Chat Model
        var azureOpenAiChatModel = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

        var documentIntelligenceInvoiceScanHelper = new DocumentIntelligenceInvoiceScanHelper(getDocumentIntelligenceClient(),getBlobStorageProxyClient());

        var accountAgent = new AccountMCPAgent(azureOpenAiChatModel,
                "bob.user@contoso.com",
                "http://localhost:8070/sse");
        var transactionHistoryAgent = new TransactionHistoryMCPAgent(azureOpenAiChatModel,
                "bob.user@contoso.com",
                "http://localhost:8090/sse",
                "http://localhost:8070/sse");
        var paymentAgent = new PaymentMCPAgent(azureOpenAiChatModel,
                documentIntelligenceInvoiceScanHelper,
                "bob.user@contoso.com",
                "http://localhost:8090/sse",
                "http://localhost:8070/sse",
                "http://localhost:8060/sse");

        var supervisorAgent = new SupervisorRoutingAgent(azureOpenAiChatModel, List.of(accountAgent,transactionHistoryAgent,paymentAgent));
        var chatHistory = new ArrayList<ChatMessage>();


        chatHistory.add(UserMessage.from("How much money do I have in my account?"));
        System.out.println(chatHistory.get(chatHistory.size()-1));
        supervisorAgent.invoke(chatHistory);
        System.out.println(chatHistory.get(chatHistory.size()-1));

        chatHistory.add(UserMessage.from("what about my visa"));
        System.out.println(chatHistory.get(chatHistory.size()-1));
        supervisorAgent.invoke(chatHistory);
        System.out.println(chatHistory.get(chatHistory.size()-1));

        chatHistory.add(UserMessage.from("When was last time I've paid contoso?"));
        System.out.println(chatHistory.get(chatHistory.size()-1));
        supervisorAgent.invoke(chatHistory);
        System.out.println(chatHistory.get(chatHistory.size()-1));

        chatHistory.add(UserMessage.from("Please pay this bill gori.png"));

        //this flow should activate the scanInvoice tool
        System.out.println(chatHistory.get(chatHistory.size()-1));
        supervisorAgent.invoke(chatHistory);
        System.out.println(chatHistory.get(chatHistory.size()-1));

        chatHistory.add(UserMessage.from("yep, they are correct"));
        System.out.println(chatHistory.get(chatHistory.size()-1));
        supervisorAgent.invoke(chatHistory);
        System.out.println(chatHistory.get(chatHistory.size()-1));


        chatHistory.add(UserMessage.from("use my visa"));
        System.out.println(chatHistory.get(chatHistory.size()-1));
        supervisorAgent.invoke(chatHistory);
        System.out.println(chatHistory.get(chatHistory.size()-1));


        chatHistory.add(UserMessage.from("yes please proceed with payment"));
        System.out.println(chatHistory.get(chatHistory.size()-1));
        supervisorAgent.invoke(chatHistory);
        System.out.println(chatHistory.get(chatHistory.size()-1));
    }
    private static BlobStorageProxy getBlobStorageProxyClient() {

        String containerName = "content";
        return new BlobStorageProxy(System.getenv("AZURE_STORAGE_ACCOUNT"),containerName,new AzureCliCredentialBuilder().build());
    }

    private static DocumentIntelligenceClient getDocumentIntelligenceClient() {
        String endpoint = "https://%s.cognitiveservices.azure.com".formatted(System.getenv("AZURE_DOCUMENT_INTELLIGENCE_SERVICE"));

        return new DocumentIntelligenceClientBuilder()
                .credential(new AzureCliCredentialBuilder().build())
                .endpoint(endpoint)
                .buildClient();
    }
}
