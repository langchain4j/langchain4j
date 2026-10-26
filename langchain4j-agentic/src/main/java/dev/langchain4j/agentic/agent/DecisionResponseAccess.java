package dev.langchain4j.agentic.agent;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.model.decision.response.DecisionResponse;

/**
 * Gives access to the last response of the decision model of a decision agent, so that it can be reported to the
 * {@link AgentListener AgentListener}s together with the agent's output. It
 * is the counterpart of {@link ChatMessagesAccess} for the agents built with
 * {@link AgenticServices#decisionAgentBuilder(Class)}. A class wrapping a decision agent must implement this
 * interface and not {@link ChatMessagesAccess} or {@code ChatMemoryAccess}, whose methods a decision agent rejects.
 *
 * @since 1.23.0
 */
@Experimental
public interface DecisionResponseAccess {

    /**
     * The response of the decision model to the last invocation of the agent in the agentic scope with the given
     * memory id, or {@code null} if the agent was not invoked in that scope or its last invocation failed.
     */
    DecisionResponse lastDecisionResponse(Object agenticScopeId);

    /**
     * Forgets the response kept for the agentic scope with the given memory id.
     */
    void removeLastDecisionResponse(Object agenticScopeId);
}
