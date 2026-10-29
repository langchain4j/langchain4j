package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.internal.ActivationConditions;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;

public record PlanningContext(AgenticScope agenticScope, AgentInvocation previousAgentInvocation) {

    /**
     * Tells whether the given subagent would run if it were invoked now, according to its
     * {@link AgentInstance#activationCondition() activation condition}. A planner never needs this for correctness,
     * because an inactive agent is skipped when invoked anyway, but a planner selecting its candidates from the state
     * of the {@link AgenticScope} can use it to avoid selecting inactive agents.
     * <p>
     * The condition is evaluated each time this method is called and once more when the agent is invoked, so the
     * agent can still be skipped if the state changed in between: the planner must handle a
     * {@link AgentInvocation#skipped() skipped} invocation anyway. A condition throwing an exception is considered to
     * hold here, so that the failure is raised, and handled by the error handler, when the agent is invoked.
     *
     * @param agent a subagent of the planner
     * @return {@code true} if the agent has no activation condition, was already activated, or its condition holds
     */
    public boolean isActive(AgentInstance agent) {
        return ActivationConditions.isActive(agent, agenticScope);
    }
}
