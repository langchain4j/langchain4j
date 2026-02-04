package dev.langchain4j.agentic.observability;

import java.util.Map;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;

public record AgentInvocationError(AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Throwable error) {

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }
}
