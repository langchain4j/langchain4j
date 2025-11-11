package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Map;

public record AgentResponse(AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Object output) {

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }
}
