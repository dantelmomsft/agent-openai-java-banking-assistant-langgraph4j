package com.microsoft.openai.samples.assistant.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public  class AccountAgent implements NodeAction<AgentWorkflowState> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountAgent.class);

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

    private AccountAgent(ChatLanguageModel model, ChatMemory memory) {

    }

    @Override
    public Map<String, Object> apply(AgentWorkflowState agentContext) throws Exception {
        return Map.of( "messages",
                AiMessage.from("Account info: name = 'bartolomeo'."), // result from reasoning
                "intent", SupervisorAgent.Intent.User.name() // force intent
        );
    }
}
