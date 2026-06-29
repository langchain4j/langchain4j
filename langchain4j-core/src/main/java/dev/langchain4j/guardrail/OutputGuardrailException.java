package dev.langchain4j.guardrail;

/**
 * Exception thrown when an output guardrail validation fails.
 * <p>
 *     This class is not intended to be thrown within guardrail implementations. It is for the framework only. It is ok to catch it.
 * </p>
 */
public final class OutputGuardrailException extends GuardrailException {
    private final OutputGuardrailResult result;

    public OutputGuardrailException(String message) {
        this(message, (Throwable) null);
    }

    public OutputGuardrailException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public OutputGuardrailException(String message, Throwable cause, OutputGuardrailResult result) {
        super(message, cause);
        this.result = result;
    }

    /**
     * Returns the {@link OutputGuardrailResult} that caused this exception, or {@code null} if not available.
     * Callers catching this exception can inspect the result to understand which guardrail failed and whether
     * the violating message was removed from memory.
     */
    public OutputGuardrailResult result() {
        return result;
    }
}
