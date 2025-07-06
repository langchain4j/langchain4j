package dev.langchain4j.guardrail;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when an input or output guardrail validation fails.
 * <p>
 *     This class is not intended to be used within guardrail implementations. It is for the framework only.
 * </p>
 * @see InputGuardrailException
 * @see OutputGuardrailException
 */
public sealed class GuardrailException extends LangChain4jException
        permits InputGuardrailException, OutputGuardrailException {
    protected GuardrailException(String message) {
        super(message);
    }

    protected GuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
