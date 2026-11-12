package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The token usage and the model name of the response of a model called by the planner of an agent, like the one a
 * supervisor asks which subagent to invoke next or the decision model of a decision router, reported to
 * {@link AgentListener#onPlannerModelResponse(PlannerModelResponse)} when the planner receives that response. These
 * are the model calls that the planner makes itself and reports through
 * {@link PlanningContext#reportChatModelResponse reportChatModelResponse} or
 * {@link PlanningContext#reportDecisionModelResponse reportDecisionModelResponse}, and not the ones of its subagents,
 * which are reported by their own invocations.
 */
public final class PlannerModelResponse {

    private final AgenticScope agenticScope;
    private final AgentInstance agent;
    private final @Nullable TokenUsage tokenUsage;
    private final @Nullable String modelName;

    /**
     * @param agenticScope the scope of the agentic system running the planner
     * @param agent the agent whose planner called the model
     * @param tokenUsage the token usage of this model call, or {@code null} if the provider didn't report it
     * @param modelName the name of the model that answered this call, or {@code null} if the provider didn't report it
     */
    public PlannerModelResponse(
            AgenticScope agenticScope,
            AgentInstance agent,
            @Nullable TokenUsage tokenUsage,
            @Nullable String modelName) {
        this.agenticScope = agenticScope;
        this.agent = agent;
        this.tokenUsage = tokenUsage;
        this.modelName = modelName;
    }

    public AgenticScope agenticScope() {
        return agenticScope;
    }

    public AgentInstance agent() {
        return agent;
    }

    /**
     * Returns the token usage of this model call, or {@code null} if the provider didn't report it.
     */
    public @Nullable TokenUsage tokenUsage() {
        return tokenUsage;
    }

    /**
     * Returns the name of the model that answered this call, or {@code null} if the provider didn't report it.
     */
    public @Nullable String modelName() {
        return modelName;
    }

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlannerModelResponse that
                && Objects.equals(agenticScope, that.agenticScope)
                && Objects.equals(agent, that.agent)
                && Objects.equals(tokenUsage, that.tokenUsage)
                && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agenticScope, agent, tokenUsage, modelName);
    }

    @Override
    public String toString() {
        return "PlannerModelResponse{agentName='" + (agent == null ? null : agent.name()) + "', modelName='"
                + modelName + "', tokenUsage=" + tokenUsage + "}";
    }
}
