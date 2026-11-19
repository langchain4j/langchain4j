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
     * tool-calling round, was rolled back (or its rollback was attempted, when the compensating action failed). The
     * tool failed by throwing, or by returning an error result that a tool guardrail kept an error (it rejected it, or
     * sanitized it with {@code successWith(String)}, which keeps {@code isError}).
     */
    TOOL_EXECUTION_FAILED,

    /**
     * The AI Service invocation was cancelled, so this tool, which had executed successfully, was rolled back (or its
     * rollback was attempted, when the compensating action failed).
     */
    INVOCATION_CANCELLED,

    /**
     * A tool input guardrail rejected the call of a tool in this invocation, so this tool, which had executed
     * successfully in the same or an earlier tool-calling round, was rolled back (or its rollback was attempted, when
     * the compensating action failed). The rejected tool did not run, so it is not rolled back itself. This applies to
     * a fatal rejection too, which fails the invocation once the rollback is done.
     *
     * @since 1.25.0
     */
    TOOL_INPUT_GUARDRAIL_FAILED,

    /**
     * A tool output guardrail turned the successful result of a tool in this invocation into an error result (it
     * rejected the result, or rewrote it with {@code isError == true}), so this tool, which had executed successfully
     * in the same or an earlier tool-calling round, was rolled back (or its rollback was attempted, when the
     * compensating action failed). The tool of the rejected result ran, so it is rolled back too, with its original
     * result. A rewrite that keeps {@code isError == false} does not roll anything back. This applies to a fatal
     * rejection too, which fails the invocation once the rollback is done.
     *
     * @since 1.25.0
     */
    TOOL_OUTPUT_GUARDRAIL_FAILED
}
