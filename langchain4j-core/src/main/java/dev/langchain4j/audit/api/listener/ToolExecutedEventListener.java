package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.ToolExecutedEvent;

/**
 * A listener for {@link ToolExecutedEvent}, which represents an event
 * that occurs after a tool is executed within an AI Service invocation.
 * This interface extends {@link AiServiceInvocationEventListener}, specializing it
 * for handling events specifically related to tool executions.
 *
 * Classes implementing this interface should handle scenarios where a tool
 * is executed during an AI Service invocation. These events may occur multiple times
 * within a single invocation, and they include details about the tool execution
 * request and the corresponding execution result.
 */
@FunctionalInterface
public interface ToolExecutedEventListener extends AiServiceInvocationEventListener<ToolExecutedEvent> {
    @Override
    default Class<ToolExecutedEvent> getEventClass() {
        return ToolExecutedEvent.class;
    }
}
