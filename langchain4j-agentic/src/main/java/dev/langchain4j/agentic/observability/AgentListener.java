package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Listener interface for monitoring agent invocations.
 */
public interface AgentListener {

    default void beforeAgentInvocation(AgentRequest agentRequest) { }
    default void afterAgentInvocation(AgentResponse agentResponse) { }
    default void onAgentInvocationError(AgentInvocationError agentInvocationError) { }

    /**
     * Called when an agent is skipped instead of being invoked, because its activation condition is false or because
     * it is optional and one of its arguments is missing. A skipped agent is not followed by any other callback.
     */
    default void onAgentSkipped(AgentSkipped agentSkipped) { }

    default void afterAgenticScopeCreated(AgenticScope agenticScope) { }
    default void beforeAgenticScopeDestroyed(AgenticScope agenticScope) { }

    default void onAgenticSystemSuspended(AgenticScope agenticScope) { }

    default void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) { }
    default void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) { }

    /**
     * Indicates whether this listener should be used only to the agent where it is registered (default)
     * or also inherited by its subagents.
     *
     * @return true if the listener should be inherited by sub-agents, false otherwise
     */
    default boolean inheritedBySubagents() {
        return false;
    }
}
