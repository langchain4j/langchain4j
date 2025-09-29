package dev.langchain4j.agentic.internal;

import java.util.Map;

public record AgentInvocationArguments(Map<String, Object> namedArgs, Object[] positionalArgs) {
}
