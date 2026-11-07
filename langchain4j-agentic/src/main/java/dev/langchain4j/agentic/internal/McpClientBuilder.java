package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.function.Predicate;

public interface McpClientBuilder<T> {

    McpClientBuilder<T> toolName(String toolName);

    McpClientBuilder<T> inputKeys(String... inputKeys);

    McpClientBuilder<T> outputKey(String outputKey);

    /**
     * Sets whether the agent should be invoked asynchronously, allowing the workflow to continue without waiting for
     * the agent's result.
     * The result is awaited only where it is read: by another agent taking it as an argument, or by the output
     * function or output key of the agentic system.
     * <p>
     * When nothing reads the result, the call to the agentic system returns while the agent is still running, and
     * the result or the failure of the agent is then reported only to its {@link AgentListener}. The next call on
     * the same memory id waits for these agents to complete before it starts.
     *
     * @param async {@code true} for asynchronous invocation
     * @return this builder
     */
    McpClientBuilder<T> async(boolean async);

    McpClientBuilder<T> listener(AgentListener agentListener);

    /**
     * Sets the condition under which the agent runs when it is invoked as a subagent of an agentic system. When the
     * condition is false the MCP tool is not called and the agent is skipped. It can be evaluated more than once, so
     * it must be free of side effects.
     */
    default McpClientBuilder<T> activationCondition(Predicate<AgenticScope> activationCondition) {
        return activationCondition("<unknown>", activationCondition);
    }

    /**
     * Sets the condition, with its description, under which the agent runs when it is invoked as a subagent of an
     * agentic system. When the condition is false the MCP tool is not called and the agent is skipped. It can be
     * evaluated more than once, so it must be free of side effects.
     */
    McpClientBuilder<T> activationCondition(String description, Predicate<AgenticScope> activationCondition);

    T build();
}
