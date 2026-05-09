package dev.langchain4j.service.tool;

/**
 * Thrown when an LLM response contains more tool execution requests than the configured
 * {@code maxToolCallsPerResponse} limit allows.
 *
 * <p>
 * This is intended for cooperative truncation when an LLM returns more tool calls in a
 * single response than the user wants to spend (for example, to bound execution cost or latency).
 *
 * @see dev.langchain4j.service.AiServices#maxToolCallsPerResponse(int)
 * @since 1.14.0
 */
public class ToolCallsLimitExceededException extends RuntimeException {

    private final int limit;
    private final int attempted;

    public ToolCallsLimitExceededException(int limit, int attempted) {
        super(String.format(
                "Exceeded maximum tool calls per response: %d (attempted: %d)",
                limit, attempted));
        this.limit = limit;
        this.attempted = attempted;
    }

    /**
     * @return the configured maximum number of tool calls per response
     */
    public int getLimit() {
        return limit;
    }

    /**
     * @return the number of tool calls present in the offending LLM response
     */
    public int getAttempted() {
        return attempted;
    }
}
