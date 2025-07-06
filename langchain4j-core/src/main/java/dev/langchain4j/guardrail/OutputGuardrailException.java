package dev.langchain4j.guardrail;

/**
 * Exception thrown when an output guardrail validation fails.
 * <p>
 *     This class is not intended to be thrown within guardrail implementations. It is for the framework only. It is ok to catch it.
 * </p>
 */
public final class OutputGuardrailException extends GuardrailException {
    public OutputGuardrailException(String message) {
        super(message);
    }

    public OutputGuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
