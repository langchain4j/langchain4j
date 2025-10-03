package dev.langchain4j.agentic.scope;

import dev.langchain4j.agentic.internal.AgentSpecification;
import java.util.Map;

public record AgentExecution(AgentSpecification agentSpec, Object agent, Map<String, Object> inputs, Object output) {
}
