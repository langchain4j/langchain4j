package dev.langchain4j.guardrail;

import org.jspecify.annotations.Nullable;

/**
 * Exception thrown when an input or output guardrail validation fails.
 * <p>
 *     This class is not intended to be used within guardrail implementations. It is for the framework only.
 * </p>
 * @see InputGuardrailException
 * @see OutputGuardrailException
 */
public sealed class GuardrailException extends RuntimeException
        permits InputGuardrailException, OutputGuardrailException {
    protected GuardrailException(String message) {
        super(message);
    }

    protected GuardrailException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
