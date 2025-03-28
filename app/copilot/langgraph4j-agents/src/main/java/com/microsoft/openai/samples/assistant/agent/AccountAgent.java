package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.openapi.OpenAPIToolsImporter;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecutor;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public  class AccountAgent implements NodeAction<AgentWorkflowState> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountAgent.class);

    private  Map<ToolSpecification, ToolExecutor> toolsSpecifications;
    private final ChatLanguageModel chatModel;

    interface Assistant {

        Result<String> chat(String userMessage);
    }

    private String ACCOUNT_AGENT_SYSTEM_MESSAGE = """
         you are a personal financial advisor who help the user to retrieve information about their bank accounts.
         Use html list or table to display the account information.
         Always use the below logged user details to retrieve account info:
         %s
        
         Before executing a function call, check data in below function calls cache:
         %s
        """;

    public static AsyncNodeAction<AgentWorkflowState> of(ChatLanguageModel model, ChatMemory memory ) {
        return node_async( new AccountAgent(model, memory ));
    }

    private AccountAgent(ChatLanguageModel chatModel, ChatMemory memory) {

        this.chatModel = chatModel;


    }

    @Override
    public Map<String, Object> apply(AgentWorkflowState agentContext) throws Exception {

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .toolSpecifications(new ArrayList<>(toolsSpecifications.keySet()))
                .build();

        //provide to the agent all the message history
        List<ChatMessage> messages = agentContext.messages();
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .parameters(parameters)
                .build();
       ChatResponse chatResponse = chatModel.chat(request);

        return Map.of( "messages",
                AiMessage.from("Account info: name = 'bartolomeo'."), // result from reasoning
                "intent", SupervisorAgent.Intent.User.name() // force intent
        );
    }
}
