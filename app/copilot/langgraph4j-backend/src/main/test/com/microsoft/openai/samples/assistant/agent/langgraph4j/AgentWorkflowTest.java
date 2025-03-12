package com.microsoft.openai.samples.assistant.agent.langgraph4j;

import com.microsoft.openai.samples.assistant.langgraph4j.AgentContext;
import com.microsoft.openai.samples.assistant.langgraph4j.AgentWorkflowBuilder;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentWorkflowTest {
    private static final Logger log = LoggerFactory.getLogger("Tests");
    @Test
    public void testWorkflow01() throws Exception {

        var workflow = new AgentWorkflowBuilder().build();

        var state = workflow.invoke( Map.of( "messages", UserMessage.from("i need the infos from my account")));

        assertTrue( state.isPresent() );
        assertFalse( state.get().clarification().isPresent() );
        assertTrue( state.get().intent().isPresent() );

        log.info( "\nIntent detected: '{}'",  state.flatMap(AgentContext::intent).orElseThrow());

    }

    @Test
    public void testWorkflow02() throws Exception {

        var workflow = new AgentWorkflowBuilder().build();

        var state = workflow.invoke( Map.of( "messages", UserMessage.from("Hello i'm a human")));

        assertTrue( state.isPresent() );
        assertFalse( state.get().intent().isPresent() );
        assertTrue( state.get().clarification().isPresent() );

        log.info( "\nClarification requested:\n'{}'",  state.flatMap(AgentContext::clarification).orElseThrow());
    }
}
