package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.function.Predicate;

public interface McpClientBuilder<T> {

    McpClientBuilder<T> toolName(String toolName);

    McpClientBuilder<T> inputKeys(String... inputKeys);

    McpClientBuilder<T> outputKey(String outputKey);

    McpClientBuilder<T> async(boolean async);

    McpClientBuilder<T> listener(AgentListener agentListener);

    /**
     * Sets the condition under which the agent runs when it is invoked as a subagent of an agentic system. When the
     * condition is false the MCP tool is not called and the agent is skipped.
     */
    default McpClientBuilder<T> activationCondition(Predicate<AgenticScope> activationCondition) {
        return activationCondition("<unknown>", activationCondition);
    }

    /**
     * Sets the condition, with its description, under which the agent runs when it is invoked as a subagent of an
     * agentic system. When the condition is false the MCP tool is not called and the agent is skipped.
     */
    McpClientBuilder<T> activationCondition(String description, Predicate<AgenticScope> activationCondition);

    T build();
}
