package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Listener interface for monitoring agent invocations.
 */
public interface AgenticListener {

    default void beforeAgentInvocation(AgentRequest agentRequest) { }
    default void afterAgentInvocation(AgentResponse agentResponse) { }
    default void onAgentInvocationError(AgentInvocationError agentInvocationError) { }

    default void onAgenticScopeCreated(AgenticScope agenticScope) { }
    default void onAgenticScopeDestroyed(AgenticScope agenticScope) { }
}
