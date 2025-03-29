package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.openapi.OpenAPIToolsImporter;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public  class AccountAgent  {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountAgent.class);

    private final OpenAPIToolsImporter openAPIToolsImporter;
    private final ChatLanguageModel chatModel;
    private final Prompt agentPrompt;

    private String ACCOUNT_AGENT_SYSTEM_MESSAGE = """
         you are a personal financial advisor who help the user to retrieve information about their bank accounts.
         Use html list or table to display the account information.
         Always use the below logged user details to retrieve account info:
         '{{loggedUserName}}'
        """;

    public AccountAgent(ChatLanguageModel chatModel, String loggedUserName, String restServerUrl) {
        if (loggedUserName == null || loggedUserName.isEmpty()) {
            throw new IllegalArgumentException("loggedUserName cannot be null or empty");
        }

        if (chatModel == null) {
            throw new IllegalArgumentException("chatModel cannot be null");
        }
        this.chatModel = chatModel;

        if (restServerUrl == null || restServerUrl.isEmpty()) {
            throw new IllegalArgumentException("restServerUrl cannot be null or empty");
        }

       PromptTemplate promptTemplate = PromptTemplate.from(ACCOUNT_AGENT_SYSTEM_MESSAGE);
       agentPrompt =promptTemplate.apply(Map.of("loggedUserName", loggedUserName));

       this.openAPIToolsImporter = OpenAPIToolsImporter.builder()
                .withToolName("account-api")
                .withSpecPath("account.yaml")
                .withServerUrl(restServerUrl)
                .build();

    }


    public void invoke(ChatMemory chatMemory) throws Exception {

      //overwrite the system message with the agent prompt
       chatMemory.add(SystemMessage.from(agentPrompt.text()));

       ChatRequestParameters parameters = ChatRequestParameters.builder()
                .toolSpecifications(openAPIToolsImporter.getToolSpecifications())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(chatMemory.messages())
                .parameters(parameters)
                .build();

       AiMessage aiMessage = chatModel.chat(request).aiMessage();

       //start ReAct tool loop planning
       while(aiMessage != null && aiMessage.hasToolExecutionRequests()){
           List<ToolExecutionResultMessage> toolExecutionResultMessages = executeToolRequests(aiMessage.toolExecutionRequests());

          //add the tool execution requests to the messages
          chatMemory.add(aiMessage);

           //add tool requests results to the messages
          toolExecutionResultMessages.forEach(chatMemory::add);

           //multiple tools execution requests are executed sequentially
           ChatRequest toolExecutionResultResponseRequest = ChatRequest.builder()
                   .messages(chatMemory.messages())
                   .parameters(parameters)
                   .build();

           aiMessage = chatModel.chat(toolExecutionResultResponseRequest).aiMessage();
       }

       //add the final result to the list of messages
       chatMemory.add(aiMessage);
    }

    private List<ToolExecutionResultMessage> executeToolRequests(List<ToolExecutionRequest> toolExecutionRequests) {

        List<ToolExecutionResultMessage> toolExecutionResultMessages = new ArrayList<>();
        for(ToolExecutionRequest toolExecutionRequest : toolExecutionRequests){
            ToolExecutor toolExecutor = openAPIToolsImporter.getToolExecutor(toolExecutionRequest.name());
            String result = toolExecutor.execute(toolExecutionRequest,null);
            toolExecutionResultMessages.add(ToolExecutionResultMessage.from(toolExecutionRequest,result));
        }
        return toolExecutionResultMessages;
    }


}
