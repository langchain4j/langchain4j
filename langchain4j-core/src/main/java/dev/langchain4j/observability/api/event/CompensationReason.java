package dev.langchain4j.observability.api.event;

/**
 * Why a successfully-executed tool was compensated (rolled back). Carried by {@link ToolCompensatedEvent}.
 *
 * @since 1.19.0
 */
public enum CompensationReason {

    /**
     * Another tool in the same tool-calling round failed, so this tool - which had executed successfully - was
     * rolled back to keep the round consistent.
     */
    TOOL_EXECUTION_FAILED,

    /**
     * The AI Service invocation was cancelled, so this tool - which had executed successfully - was rolled back.
     */
    INVOCATION_CANCELLED
}
