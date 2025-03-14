package com.microsoft.openai.samples.assistant.agent.agent;

import com.microsoft.openai.samples.assistant.agent.AgentContext;
import com.microsoft.openai.samples.assistant.agent.AgentWorkflowBuilder;
import com.microsoft.openai.samples.assistant.agent.SupervisorAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AgentWorkflowTest {
    private static final Logger log = LoggerFactory.getLogger("Tests");

    @Test
    public void generateGraphRepresentation() throws Exception {
        var workflow = new AgentWorkflowBuilder().build();

        var result = workflow.getGraph( GraphRepresentation.Type.MERMAID, "Banking Assistant", false);

        System.out.println( result.content() );

    }

    @Test
    public void testWorkflow01() throws Exception {

        var workflow = new AgentWorkflowBuilder().build();

        var userRequest = "i need the infos from my account";

        log.info( "\nrequest by User:\n{}", userRequest );

        var state = workflow.invoke( Map.of( "messages", UserMessage.from( userRequest ) ));

        assertTrue( state.isPresent() );
        //assertFalse( state.get().clarification().isPresent() );
        assertTrue( state.get().intent().isPresent() );
        assertTrue( state.get().lastMessage().isPresent() );

        log.info( "\nresponse to User:\n{}",
                state.get().lastMessage()
                        .map(AiMessage.class::cast)
                        .map(AiMessage::text)
                        .orElseThrow());

    }

    @Test
    public void testWorkflow02() throws Exception {

        var workflow = new AgentWorkflowBuilder().build();

        var runnableConfig =  RunnableConfig.builder()
                .threadId("conversation-1" )
                .build();

        var userRequest1 = "Hello i'm a bartolomeo";

        log.info( "\nrequest by User:\n{}",userRequest1 );

        var state = workflow.invoke( Map.of( "messages", UserMessage.from(userRequest1)), runnableConfig);

        assertTrue( state.isPresent() );
        assertEquals( SupervisorAgent.Intent.User.name(), state.get().intent().get() );
        assertTrue( state.get().clarification().isPresent() );

        log.info( "\nresponse to User::\n{}",  state.flatMap(AgentContext::clarification).orElseThrow());


        var snapshot = workflow.getState(runnableConfig);
        assertEquals(SupervisorAgent.Intent.User.name(), snapshot.next() );

        var userRequest2 = "i need the infos from my account";

        log.info( "\nrequest by User:\n{}",userRequest2 );

        var partialState = new HashMap<String,Object>() {{
            put( "messages", UserMessage.from(userRequest2) );
            put( "clarification", null); // remove from state
            put( "intent", null); // remove from state
        }};

        runnableConfig = workflow.updateState( runnableConfig, partialState);

        state = workflow.invoke( null, runnableConfig);

        assertTrue( state.isPresent() );
        assertFalse( state.get().clarification().isPresent() );
        assertTrue( state.get().intent().isPresent() );
        assertEquals(SupervisorAgent.Intent.User.name(), state.get().intent().get() );
        assertTrue( state.get().lastMessage().isPresent() );

        log.info( "\nresponse to User:\n{}",
                state.get().lastMessage()
                        .map(AiMessage.class::cast)
                        .map(AiMessage::text)
                        .orElseThrow());

    }
}
