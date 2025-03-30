package com.microsoft.openai.samples.assistant.agent;

public class AgentExecutionException extends RuntimeException {
    public AgentExecutionException(String message) {
        super(message);
    }

    public AgentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}