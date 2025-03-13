package com.microsoft.openai.samples.assistant.langgraph4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;


/**
 * Implements methods for managing a financial advisor node that processes user intents.
 * The class provides functionality to evaluate user messages and determine the appropriate action based on pre-defined
 * intent patterns.
 */
public class SupervisorAgent implements NodeAction<AgentContext> {
    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    public static String Clarification = "Clarification";

    public enum Intent {
        BillPayment,
        RepeatTransaction,
        TransactionHistory,
        AccountInfo;

        /**
         * Returns a list of all possible names of the {@code Intent} enum constants.
         *
         * @return an unmodifiable list containing the names of the {@code Intent} enum constants
         */
        public static List<String> names() {
            return Arrays.stream(Intent.values())
                    .map(Enum::name)
                    .toList();
        }
    }


    /**
     * Represents a router that handles intents and clarifications.
     *
     * <ul>
     *     <li>Intent to route to the next step. If no intent is identified, routes to "Clarification."</li>
     *     <li>If you don't understand or if an intent is not identified, ask a clarifying question using
     *         the list of available intents. Otherwise, return null.</li>
     * </ul>
     */
    static class Router {
        // @Description("Intent to route to next. If no intent are identified route to FINISH.")
        @Description("Intent to route to next. If no intent are identified route to Clarification.")
        String intent   ;

        //@Description("If you don't understand or if an intent is not identified be polite with the user, ask clarifying question also using the list of the available intents.")
        @Description("""
        If you don't understand or if an intent is not identified be polite with the user,
        ask clarifying question using the list of the available intents. Otherwise null.
        """)
        String clarification;

        /**
         * Returns a string representation of this router.
         * {@code toString()} concatenates the intent and clarification of the router into a single string.
         *
         * @return the formatted string representation of this router
         */
        @Override
        public String toString() {
            return format( "Router[intent: %s, clarification: %s]", intent, clarification);
        }

        /**
         * Converts the current state of the {@code SupervisorAgent} instance into a map.
         *
         * @return A {@link Map} containing either "intent" or "clarification" based on the current state, if any. If neither is available, throws an {@link IllegalStateException}.
         */
        public Map<String,Object> toMap() {
            if(Clarification.equalsIgnoreCase(intent) ) {
                return Map.of("clarification", clarification );
            }
            else if( clarification == null ) {
                return Map.of("intent", intent );
            }

            throw new IllegalStateException("SupervisorAgent could not infer either 'intent' or 'clarification'!");
        }
    }

    /**
     * Represents a service interface for handling banking operations.
     * This service is designed to assist bank customers in managing their accounts and services.
     * It can handle various tasks such as reviewing account details, transactions history,
     * checking payments history for specific payees, and managing recurrent bill payments.
     *
     */
    interface Service {
        /**
         * Evaluates the user's message and determines the intent from a list of available intents.
         *
         * @param intents A JSON array containing the available intents (e.g., "CheckBalance", "PayBills").
         * @param userMessage The user's input message as a JSON string containing details about the request.
         * @return A {@link Router} representing the detected intent based on the user's message and available intents.
         */
        @SystemMessage( """
        You are a personal financial advisor who help bank customers manage their banking accounts and services.
        The user may need help with his recurrent bill payments, it may start the payment checking payments history for a specific payee.
        In other cases it may want to just review account details or transactions history.
        Based on the conversation you need to identify the user intent.
        The available intents are:
        {{intents}}
        
        Don't add any comments in the output or other characters, just use json format.
        """)
        Router evaluate(@V("intents") String intents, @dev.langchain4j.service.UserMessage  String userMessage);
    }

    private final Service service;

    /**
     * Creates an {@code AsyncNodeAction} with a new {@code SupervisorAgent}.
     *
     * @param model the chat language model to use
     * @return the created {@code AsyncNodeAction}
     */
    public static AsyncNodeAction<AgentContext> of(ChatLanguageModel model ) {
        return node_async( new SupervisorAgent(model ));
    }

    /**
     * Constructs a new instance of {@code SupervisorAgent}.
     *
     * @param model the {@link ChatLanguageModel} to be used by this agent.
     */
    private SupervisorAgent( ChatLanguageModel model ) {

        service = AiServices.create( Service.class, model );
    }

    /**
     * Applies the given agent context to determine a route.
     * 
     * @param state The current state of the agent.
     * @return A map representing the calculated route.
     */
    @Override
    public Map<String, Object> apply(AgentContext state) {
        var m = String.join(",", Intent.names());
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