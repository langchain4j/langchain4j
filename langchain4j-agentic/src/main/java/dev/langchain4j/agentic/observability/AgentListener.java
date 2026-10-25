package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import java.util.List;

/**
 * Listener interface for monitoring agent invocations.
 */
public interface AgentListener {

    default void beforeAgentInvocation(AgentRequest agentRequest) {}

    default void afterAgentInvocation(AgentResponse agentResponse) {}

    default void onAgentInvocationError(AgentInvocationError agentInvocationError) {}

    /**
     * Called when an agent is skipped instead of being invoked, because its activation condition is false or because
     * it is optional and one of its arguments is missing. A skipped agent is not followed by any other callback.
     */
    default void onAgentSkipped(AgentSkipped agentSkipped) {}

    default void afterAgenticScopeCreated(AgenticScope agenticScope) {}

    default void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {}

    default void onAgenticSystemSuspended(AgenticScope agenticScope) {}

    default void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {}

    default void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {}

    /**
     * Called for each event fired by the AI Service of an LLM-based agent while it is invoked, from the
     * {@code AiServiceStartedEvent} to the completion, error or cancellation of the AI Service invocation, including
     * the requests sent to the model, the responses received from it and the tool executions.
     * It is not called for the {@code AiServiceInvocationSummaryEvent}, which only aggregates these same events.
     */
    default void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {}

    /**
     * Indicates whether this listener should be used only to the agent where it is registered (default)
     * or also inherited by its subagents.
     *
     * @return true if the listener should be inherited by sub-agents, false otherwise
     */
    default boolean inheritedBySubagents() {
        return false;
    }

    /**
     * Creates an {@link AgentListener} that notifies the given {@link AiServiceListener}s of the
     * {@linkplain #onAgentAiServiceEvent(AgentAiServiceEvent) AI Service events} of the agent it is registered on,
     * so that listeners written for AI Services can be reused for agents. As an AI Service does, it notifies each
     * listener only of the events of its {@link AiServiceListener#getEventClass() event class}, and a listener that
     * throws is logged without preventing the others from being notified.
     *
     * @param inheritedBySubagents whether the listener is also inherited by the subagents of the agent it is
     *                             registered on, see {@link #inheritedBySubagents()}
     * @param aiServiceListeners the listeners to notify, none of them listening to the
     *                           {@code AiServiceInvocationSummaryEvent}, which is not notified to agent listeners
     * @return the agent listener
     * @throws IllegalArgumentException if one of the listeners listens to the {@code AiServiceInvocationSummaryEvent}
     */
    static AgentListener forAiServiceListeners(
            boolean inheritedBySubagents, AiServiceListener<?>... aiServiceListeners) {
        return new AiServiceListenersAgentListener(inheritedBySubagents, List.of(aiServiceListeners));
    }
}
