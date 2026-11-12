package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The token usage and the model name of the response of a model called by the planner of an agent, like the one a
 * supervisor asks which subagent to invoke next or the decision model of a decision router, reported to
 * {@link AgentListener#onPlannerModelResponse(PlannerModelResponse)} when the planner receives that response. These
 * are the model calls that the planner makes itself and reports through
 * {@link PlanningContext#reportChatModelResponse reportChatModelResponse} or
 * {@link PlanningContext#reportDecisionModelResponse reportDecisionModelResponse}, and not the ones of its subagents,
 * which are reported by their own invocations.
 *
 * @param agent the agent whose planner called the model
 * @param tokenUsage the token usage of this model call, or {@code null} if the provider didn't report it
 * @param modelName the name of the model that answered this call, or {@code null} if the provider didn't report it
 */
public record PlannerModelResponse(
        AgenticScope agenticScope, AgentInstance agent, TokenUsage tokenUsage, String modelName) {

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }
}
