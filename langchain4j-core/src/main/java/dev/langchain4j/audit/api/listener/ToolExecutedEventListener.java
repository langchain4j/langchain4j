package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.ToolExecutedEvent;

/**
 * A listener for {@link ToolExecutedEvent}, which represents an event
 * that occurs when a tool execution is triggered within a large language model (LLM) interaction.
 * This interface extends {@link AiServiceInvocationEventListener}, specializing it
 * for handling events specifically related to tool executions.
 *
 * Classes implementing this interface should handle scenarios where a tool
 * is executed during an LLM interaction. These events may occur multiple times
 * within a single interaction, and they include details about the tool execution
 * request and the corresponding execution result.
 */
@FunctionalInterface
public interface ToolExecutedEventListener extends AiServiceInvocationEventListener<ToolExecutedEvent> {
    @Override
    default Class<ToolExecutedEvent> getEventClass() {
        return ToolExecutedEvent.class;
    }
}
