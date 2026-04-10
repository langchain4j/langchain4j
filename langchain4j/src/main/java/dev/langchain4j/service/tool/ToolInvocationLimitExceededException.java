package dev.langchain4j.service.tool;

/**
 * Thrown when a tool's per-invocation limit is exceeded and
 * {@link ToolLimitExceededBehavior#ERROR} is configured.
 *
 * @since 1.14.0
 */
public class ToolInvocationLimitExceededException extends RuntimeException {

    private final String toolName;
    private final int limit;
    private final int invocationCount;

    public ToolInvocationLimitExceededException(String toolName, int limit, int invocationCount) {
        super(String.format(
                "Tool '%s' has exceeded its invocation limit of %d (attempted invocation #%d)",
                toolName, limit, invocationCount));
        this.toolName = toolName;
        this.limit = limit;
        this.invocationCount = invocationCount;
    }

    public String toolName() {
        return toolName;
    }

    public int limit() {
        return limit;
    }

    public int invocationCount() {
        return invocationCount;
    }
}
