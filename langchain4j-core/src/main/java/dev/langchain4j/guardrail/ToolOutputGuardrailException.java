package dev.langchain4j.guardrail;

import dev.langchain4j.Experimental;

/**
 * Exception thrown when a fatal {@link ToolOutputGuardrail} failure aborts the AI service invocation.
 * It is thrown after a tool was executed.
 * <p>
 *     This class is not intended to be thrown within guardrail implementations. It is for the framework only. It is ok to catch it.
 * </p>
 *
 * @since 1.25.0
 */
@Experimental
public final class ToolOutputGuardrailException extends GuardrailException {
    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public ToolOutputGuardrailException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public ToolOutputGuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
