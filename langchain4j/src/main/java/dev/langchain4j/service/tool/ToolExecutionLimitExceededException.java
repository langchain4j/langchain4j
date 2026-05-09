package dev.langchain4j.service.tool;

/**
 * Thrown when a tool's per-execution limit is exceeded and
 * {@link ToolLimitExceededBehavior#ERROR} is configured.
 *
 * @since 1.14.0
 */
public class ToolExecutionLimitExceededException extends RuntimeException {

    private final String toolName;
    private final int limit;
    private final int executionCount;

    public ToolExecutionLimitExceededException(String toolName, int limit, int executionCount) {
        super(String.format(
                "Tool '%s' has exceeded its execution limit of %d (attempted execution #%d)",
                toolName, limit, executionCount));
        this.toolName = toolName;
        this.limit = limit;
        this.executionCount = executionCount;
    }

    public String toolName() {
        return toolName;
    }

    public int limit() {
        return limit;
    }

    public int executionCount() {
        return executionCount;
    }
}
