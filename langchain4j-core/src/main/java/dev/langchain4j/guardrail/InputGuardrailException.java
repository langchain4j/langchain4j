package dev.langchain4j.guardrail;

/**
 * Exception thrown when an input guardrail validation fails.
 * <p>
 *     This class is not intended to be thrown within guardrail implementations. It is for the framework only. It is ok to catch it.
 * </p>
 */
public final class InputGuardrailException extends GuardrailException {
    public InputGuardrailException(String message) {
        super(message);
    }

    public InputGuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
