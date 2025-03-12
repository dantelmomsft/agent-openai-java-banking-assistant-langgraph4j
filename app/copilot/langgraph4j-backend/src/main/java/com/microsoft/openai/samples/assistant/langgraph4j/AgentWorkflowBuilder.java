package com.microsoft.openai.samples.assistant.langgraph4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class AgentWorkflowBuilder {

    public CompiledGraph<AgentContext> build() throws GraphStateException {

        final var modelThinking = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .responseFormat( ResponseFormat.JSON )
                .modelName("deepseek-r1:14b")
                .build();

        var serializer = new StateSerializer();

        var graph = new StateGraph<>( AgentContext.SCHEMA, serializer );

        graph.addNode( "supervisor", SupervisorAgent.of(modelThinking) );
        graph.addEdge(  START, "supervisor" );
        graph.addEdge( "supervisor", END );

        var config = CompileConfig.builder().build();

        return graph.compile(config);
    }
}


class StateSerializer extends ObjectStreamStateSerializer<AgentContext> {

    public StateSerializer() {
        super(AgentContext::new);

        mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        mapper().register(ChatMessage.class, new ChatMesssageSerializer());
    }
}

class SupervisorAgent implements NodeAction<AgentContext> {
    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    static class Router {
        // @Description("Intent to route to next. If no intent are identified route to FINISH.")
        @Description("Intent to route to next. If no intent are identified route to UNDEFINED.")
        String intent   ;

        //@Description("If you don't understand or if an intent is not identified be polite with the user, ask clarifying question also using the list of the available intents.")
        @Description("""
        If you don't understand or if an intent is not identified be polite with the user,
        ask clarifying question using the list of the available intents. Otherwise null.
        """)
        String clarification;

        @Override
        public String toString() {
            return format( "Router[next: %s, clarification: %s]", intent, clarification);
        }

        public Map<String,Object> toMap() {
            if( "UNDEFINED".equalsIgnoreCase(intent) ) {
                return Map.of("clarification", clarification );
            }
            else if( clarification == null ) {
                return Map.of("intent", intent );
            }

            throw new IllegalStateException("SupervisorAgent could not infer either 'intent' or 'clarification'!");
        }
    }

    interface Service {
        @SystemMessage( """
        You are a personal financial advisor who help bank customers manage their banking accounts and services.
        The user may need help with his recurrent bill payments, it may start the payment checking payments history for a specific payee.
        In other cases it may want to just review account details or transactions history.
        Based on the conversation you need to identify the user intent.
        The available intents are:
        {{intents}}
        
        Don't add any comments in the output or other characters, just use json format.
        """)
        Router evaluate(@V("intents") String members, @dev.langchain4j.service.UserMessage  String userMessage);
    }

    final Service service;
    public final String[] members = {
            "BillPayment",
            "RepeatTransaction",
            "TransactionHistory",
            "AccountInfo"
    };

    public static AsyncNodeAction<AgentContext> of( ChatLanguageModel model ) {
        return node_async( new SupervisorAgent(model ));
    }
    private SupervisorAgent( ChatLanguageModel model ) {

        service = AiServices.create( Service.class, model );
    }

    @Override
    public Map<String, Object> apply(AgentContext state) {
        var m = String.join(",", members);
        var message = state.lastMessage().orElseThrow();
        var text = switch( message.type() ) {
            case USER -> ((UserMessage)message).singleText();
            case AI -> ((AiMessage)message).text();
            default -> throw new IllegalStateException("unexpected message type: " + message.type() );
        };

        var route = service.evaluate( m, text );

        log.debug( "Route {}", route);

        return route.toMap();

    }
}

