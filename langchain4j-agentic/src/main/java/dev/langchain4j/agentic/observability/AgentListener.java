package dev.langchain4j.agentic.observability;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * Listener interface for monitoring agent invocations.
 */
public interface AgentListener {

    default void beforeAgentInvocation(AgentRequest agentRequest) { }
    default void afterAgentInvocation(AgentResponse agentResponse) { }
    default void onAgentInvocationError(AgentInvocationError agentInvocationError) { }

    default void afterAgenticScopeCreated(AgenticScope agenticScope) { }
    default void beforeAgenticScopeDestroyed(AgenticScope agenticScope) { }

    default void beforeToolExecution(ToolExecutionRequest toolExecutionRequest) { }
    default void afterToolExecution(ToolExecutionRequest toolExecutionRequest, ToolExecutionResult toolExecutionResult) { }

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
