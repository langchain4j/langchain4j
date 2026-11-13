package dev.langchain4j.guardrail;

import dev.langchain4j.Experimental;

/**
 * Exception thrown when a fatal {@link ToolInputGuardrail} failure aborts the AI service invocation.
 * It is thrown before a tool is executed, so the tool was not executed.
 * <p>
 *     This class is not intended to be thrown within guardrail implementations. It is for the framework only. It is ok to catch it.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public final class ToolInputGuardrailException extends GuardrailException {
    public ToolInputGuardrailException(String message) {
        super(message);
    }

    public ToolInputGuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
