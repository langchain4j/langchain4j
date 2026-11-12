package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The response of a model called by the planner of an agent, like the one a supervisor asks which subagent to invoke
 * next or the decision model of a decision router, reported to
 * {@link AgentListener#onPlannerModelResponse(PlannerModelResponse)} when the planner receives it. These are the model
 * calls that the planner makes itself and reports through
 * {@link PlanningContext#reportChatModelResponse reportChatModelResponse} or
 * {@link PlanningContext#reportDecisionModelResponse reportDecisionModelResponse}, and not the ones of its subagents,
 * which are reported by their own invocations.
 * <p>
 * Exactly one of {@code chatResponse} and {@code decisionResponse} is set.
 *
 * @param agent the agent whose planner called the model
 * @param chatResponse the response of the chat model called by the planner, or {@code null} if it called a decision
 *                     model
 * @param decisionResponse the response of the decision model called by the planner, with its answers and their
 *                         probabilities, or {@code null} if it called a chat model
 */
public record PlannerModelResponse(
        AgenticScope agenticScope, AgentInstance agent, ChatResponse chatResponse, DecisionResponse decisionResponse) {

    /**
     * Returns the token usage of this model call, or {@code null} if the provider didn't report it.
     */
    public TokenUsage tokenUsage() {
        return chatResponse != null ? chatResponse.tokenUsage() : decisionResponse.tokenUsage();
    }

    /**
     * Returns the name of the model that answered this call, or {@code null} if the provider didn't report it.
     */
    public String modelName() {
        return chatResponse != null ? chatResponse.modelName() : decisionResponse.modelName();
    }

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }
}
