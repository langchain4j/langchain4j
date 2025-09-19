package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.scope.AgenticScope;

public record AgentResponse(AgenticScope agenticScope, String agentName, Object[] inputs, Object output) {
}
