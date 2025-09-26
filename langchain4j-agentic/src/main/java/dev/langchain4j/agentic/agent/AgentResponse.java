package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Map;

public record AgentResponse(AgenticScope agenticScope, String agentName, Map<String, Object> inputs, Object output) {
}
