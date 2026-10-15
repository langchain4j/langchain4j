package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Notifies that an agent was skipped instead of being invoked, either because its activation condition was false or
 * because it is optional and one of its arguments was missing.
 *
 * @param agenticScope the scope of the agentic system invoking the agent
 * @param agent the skipped agent
 * @param reason why the agent was skipped
 */
public record AgentSkipped(AgenticScope agenticScope, AgentInstance agent, String reason) {

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }
}
