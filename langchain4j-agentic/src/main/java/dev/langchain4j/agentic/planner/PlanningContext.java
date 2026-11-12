package dev.langchain4j.agentic.planner;

import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.onPlannerModelResponse;

import dev.langchain4j.agentic.internal.ActivationConditions;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.PlannerModelResponse;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The context in which a {@link Planner} decides its next action.
 *
 * @param previousAgentInvocation the invocation of the subagent that just completed, or {@code null} for the first
 *                                action
 * @param plannerAgent the agent running the planner, or {@code null} when the context is not created by an agentic
 *                     system, for instance in a unit test of the planner
 */
public record PlanningContext(
        AgenticScope agenticScope, AgentInvocation previousAgentInvocation, AgentInstance plannerAgent) {

    public PlanningContext(AgenticScope agenticScope, AgentInvocation previousAgentInvocation) {
        this(agenticScope, previousAgentInvocation, null);
    }

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

    /**
     * Reports the response of a chat model that the planner called itself to decide what to do. Its token usage and
     * its model name are notified to the {@link AgentListener#onPlannerModelResponse(PlannerModelResponse) listeners}
     * of the agent running the planner, so that the token usage is counted on the invocation of that agent, for
     * instance by the {@code AgentMonitor}: a model call that is not reported is invisible to them. Call it as soon
     * as the model returns, once for each call.
     * <p>
     * It does nothing when this context has no {@link #plannerAgent() planner agent}, or when the response is
     * {@code null}.
     *
     * @param chatResponse the response returned by the chat model
     */
    public void reportChatModelResponse(ChatResponse chatResponse) {
        if (chatResponse != null) {
            reportModelResponse(chatResponse.tokenUsage(), chatResponse.modelName());
        }
    }

    /**
     * Reports the response of a decision model that the planner called itself to decide what to do, like
     * {@link #reportChatModelResponse(ChatResponse)} does for a chat model.
     *
     * @param decisionResponse the response returned by the decision model
     */
    public void reportDecisionModelResponse(DecisionResponse decisionResponse) {
        if (decisionResponse != null) {
            reportModelResponse(decisionResponse.tokenUsage(), decisionResponse.modelName());
        }
    }

    private void reportModelResponse(TokenUsage tokenUsage, String modelName) {
        if (plannerAgent instanceof InternalAgent agent) {
            onPlannerModelResponse(
                    agent.listener(), new PlannerModelResponse(agenticScope, plannerAgent, tokenUsage, modelName));
        }
    }
}
