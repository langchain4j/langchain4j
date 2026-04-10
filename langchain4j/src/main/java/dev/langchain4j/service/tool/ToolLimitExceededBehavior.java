package dev.langchain4j.service.tool;

/**
 * Defines the behavior when a per-tool invocation limit is exceeded.
 *
 * @since 1.14.0
 */
public enum ToolLimitExceededBehavior {

    /**
     * The over-budget tool call is not executed. Instead, an error
     * {@link dev.langchain4j.data.message.ToolExecutionResultMessage} is returned to the LLM,
     * and the tool loop continues. The exhausted tool will be removed from the tool set
     * before the next LLM call.
     * <p>
     * This is the default behavior.
     */
    CONTINUE,

    /**
     * A {@link ToolInvocationLimitExceededException} is thrown, terminating the AI service invocation.
     */
    ERROR,

    /**
     * The tool loop is halted immediately. One final LLM call is made with no tools
     * available, allowing the model to produce a text answer based on the tool results
     * collected so far.
     */
    END
}
