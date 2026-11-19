package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;
/**
 * Why the compensating action of a successfully-executed tool was run to roll the tool back. Carried by
 * {@link ToolCompensatedEvent}, which is also fired when the compensating action failed (see
 * {@link ToolCompensatedEvent#compensationError()}).
 *
 * @since 1.20.0
 */
@Experimental
public enum CompensationReason {

    /**
     * A tool failed in this invocation, so this tool, which had executed successfully in the same or an earlier
     * tool-calling round, was rolled back (or its rollback was attempted, when the compensating action failed).
     */
    TOOL_EXECUTION_FAILED,

    /**
     * The AI Service invocation was cancelled, so this tool, which had executed successfully, was rolled back (or its
     * rollback was attempted, when the compensating action failed).
     */
    INVOCATION_CANCELLED
}
