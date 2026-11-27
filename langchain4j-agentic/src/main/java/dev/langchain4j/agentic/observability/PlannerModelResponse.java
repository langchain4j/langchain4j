package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The response of a model called by the planner of an agent, like the one a supervisor asks which subagent to invoke
 * next or the decision model of a decision router, with its token usage and its model name, reported to
 * {@link AgentListener#onPlannerModelResponse(PlannerModelResponse)} when the planner receives that response. These
 * are the model calls that the planner makes itself and reports through
 * {@link PlanningContext#reportChatModelResponse reportChatModelResponse} or
 * {@link PlanningContext#reportDecisionModelResponse reportDecisionModelResponse}, and not the ones of its subagents,
 * which are reported by their own invocations.
 * <p>
 * A response reported by a planner is either a {@link #chatResponse() chat response} or a
 * {@link #decisionResponse() decision response}, the other one being {@code null}, while the
 * {@link #tokenUsage() token usage} and the {@link #modelName() model name} are available for both.
 */
public final class PlannerModelResponse {

    private final AgenticScope agenticScope;
    private final AgentInstance agent;
    private final @Nullable TokenUsage tokenUsage;
    private final @Nullable String modelName;
    private final @Nullable ChatResponse chatResponse;
    private final @Nullable DecisionResponse decisionResponse;

    /**
     * Creates a notification with the token usage and the model name only, without the response of the model.
     *
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
        this(agenticScope, agent, tokenUsage, modelName, null, null);
    }

    /**
     * @param agenticScope the scope of the agentic system running the planner
     * @param agent the agent whose planner called the model
     * @param tokenUsage the token usage of this model call, or {@code null} if the provider didn't report it
     * @param modelName the name of the model that answered this call, or {@code null} if the provider didn't report it
     * @param chatResponse the response of the chat model called by the planner, or {@code null} if it called a
     *                     decision model
     * @param decisionResponse the response of the decision model called by the planner, with its answers and their
     *                         probabilities, or {@code null} if it called a chat model
     */
    public PlannerModelResponse(
            AgenticScope agenticScope,
            AgentInstance agent,
            @Nullable TokenUsage tokenUsage,
            @Nullable String modelName,
            @Nullable ChatResponse chatResponse,
            @Nullable DecisionResponse decisionResponse) {
        this.agenticScope = agenticScope;
        this.agent = agent;
        this.tokenUsage = tokenUsage;
        this.modelName = modelName;
        this.chatResponse = chatResponse;
        this.decisionResponse = decisionResponse;
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

    /**
     * Returns the response of the chat model called by the planner, or {@code null} if it called a decision model.
     */
    public @Nullable ChatResponse chatResponse() {
        return chatResponse;
    }

    /**
     * Returns the response of the decision model called by the planner, with its answers and their probabilities, or
     * {@code null} if it called a chat model.
     */
    public @Nullable DecisionResponse decisionResponse() {
        return decisionResponse;
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
                && Objects.equals(modelName, that.modelName)
                && Objects.equals(chatResponse, that.chatResponse)
                && Objects.equals(decisionResponse, that.decisionResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agenticScope, agent, tokenUsage, modelName, chatResponse, decisionResponse);
    }

    @Override
    public String toString() {
        return "PlannerModelResponse{agentName='" + (agent == null ? null : agent.name()) + "', modelName='"
                + modelName + "', tokenUsage=" + tokenUsage + "}";
    }
}
