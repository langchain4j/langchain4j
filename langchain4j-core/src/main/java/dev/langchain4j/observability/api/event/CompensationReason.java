package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;

/**
 * Why a successfully-executed tool was compensated (rolled back). Carried by {@link ToolCompensatedEvent}.
 *
 * @since 1.20.0
 */
@Experimental
public enum CompensationReason {

    /**
     * A tool in the same tool-calling round, or in an earlier round of the invocation, ended with an error result
     * the LLM sees, so this tool - which had executed successfully - was rolled back to keep the invocation
     * consistent. The error result is a tool that threw, or an error result that a tool guardrail kept an error
     * (it rejected it, or sanitized it with {@code successWith(String)}, which keeps {@code isError}).
     */
    TOOL_EXECUTION_FAILED,

    /**
     * A tool input guardrail rejected the call of a tool in the same tool-calling round, or in an earlier round of
     * the invocation, so this tool - which had executed successfully - was rolled back. The rejected tool did not run,
     * so it is not rolled back itself.
     *
     * @since 1.24.0
     */
    TOOL_INPUT_GUARDRAIL_FAILED,

    /**
     * A tool output guardrail turned the successful result of a tool in the same tool-calling round, or in an earlier
     * round of the invocation, into an error result (it rejected the result, or rewrote it with
     * {@code isError == true}), so this tool - which had executed successfully - was rolled back. The tool of the
     * rejected result ran, so it is rolled back too, with its original result. A rewrite that keeps
     * {@code isError == false} does not roll anything back.
     *
     * @since 1.24.0
     */
    TOOL_OUTPUT_GUARDRAIL_FAILED,

    /**
     * The AI Service invocation was cancelled, so this tool - which had executed successfully - was rolled back.
     */
    INVOCATION_CANCELLED
}
