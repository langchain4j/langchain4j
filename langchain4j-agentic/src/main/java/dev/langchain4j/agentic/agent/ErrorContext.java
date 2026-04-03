package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.scope.AgenticScope;

public record ErrorContext(String agentName, AgenticScope agenticScope, AgentInvocationException exception) {


}
