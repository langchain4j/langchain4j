package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.ToolCompensatedEvent;

/**
 * A listener for {@link ToolCompensatedEvent}, which represents an event that occurs after a successfully-executed
 * tool is compensated (rolled back) within an AI Service invocation. This interface extends
 * {@link AiServiceListener}, specializing it for handling tool-compensation events.
 *
 * These events may occur multiple times within a single invocation, and they include the compensated tool's
 * request, its original result, and the reason it was rolled back.
 *
 * @since 1.19.0
 */
@FunctionalInterface
public interface ToolCompensatedEventListener extends AiServiceListener<ToolCompensatedEvent> {
    @Override
    default Class<ToolCompensatedEvent> getEventClass() {
        return ToolCompensatedEvent.class;
    }
}
