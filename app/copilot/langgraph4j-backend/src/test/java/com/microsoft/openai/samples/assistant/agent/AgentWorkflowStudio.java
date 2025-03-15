package com.microsoft.openai.samples.assistant.agent;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

public class AgentWorkflowStudio {

    @SpringBootApplication
    public static class LangGraphStudioApplication {

        public static void main(String[] args) {

            SpringApplication.run(LangGraphStudioApplication.class, args);
        }

    }

    @Configuration
    public static class LangGraphStudioSampleConfig extends AbstractLangGraphStudioConfig {

        final LangGraphFlow flow;

        public LangGraphStudioSampleConfig() throws GraphStateException {
            this.flow = agentWorkflow();
        }

        private LangGraphFlow agentWorkflow() throws GraphStateException {

            var workflow = new AgentWorkflowBuilder().stateGraph();

            return  LangGraphFlow.builder()
                    .title("LangGraph Studio (Sample)")
                    .stateGraph( workflow )
                    .build();

        }

        @Override
        public LangGraphFlow getFlow() {
            return this.flow;
        }
    }

}
