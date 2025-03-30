package com.microsoft.openai.samples.assistant.agent;

import java.util.List;

public record AgentMetadata(String description, List<String> intents) {
}

