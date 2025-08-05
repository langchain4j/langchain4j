package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.cognisphere.Cognisphere;

public record ErrorContext(String agentName, Cognisphere cognisphere, AgentInvocationException exception) {


}
