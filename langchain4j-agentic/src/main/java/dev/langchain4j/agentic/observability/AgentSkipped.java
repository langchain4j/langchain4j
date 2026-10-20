package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Objects;

/**
 * Notifies that an agent was skipped instead of being invoked, either because its activation condition was false or
 * because it is optional and one of its arguments was missing.
 */
public final class AgentSkipped {

    private final AgenticScope agenticScope;
    private final AgentInstance agent;
    private final String reason;

    /**
     * @param agenticScope the scope of the agentic system invoking the agent
     * @param agent the skipped agent
     * @param reason why the agent was skipped
     */
    public AgentSkipped(AgenticScope agenticScope, AgentInstance agent, String reason) {
        this.agenticScope = agenticScope;
        this.agent = agent;
        this.reason = reason;
    }

    public AgenticScope agenticScope() {
        return agenticScope;
    }

    public AgentInstance agent() {
        return agent;
    }

    public String reason() {
        return reason;
    }

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AgentSkipped that
                && Objects.equals(agenticScope, that.agenticScope)
                && Objects.equals(agent, that.agent)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agenticScope, agent, reason);
    }

    @Override
    public String toString() {
        return "AgentSkipped{agentName='" + (agent == null ? null : agent.name()) + "', reason='" + reason + "'}";
    }
}
