package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.scope.AgenticScope;

public record AgentRequest(AgenticScope agenticScope, String agentName, Object[] inputs) {
}
