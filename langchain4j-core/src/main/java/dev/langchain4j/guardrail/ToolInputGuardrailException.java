package dev.langchain4j.guardrail;

import dev.langchain4j.Experimental;
import java.util.Optional;

/**
 * Exception thrown when a fatal {@link ToolInputGuardrail} failure aborts the AI service invocation.
 * It is thrown before a tool is executed, so the tool was not executed.
 * <p>
 *     This class is not intended to be thrown within guardrail implementations. It is for the framework only. It is ok to catch it.
 * </p>
 *
 * @since 1.25.0
 */
@Experimental
public final class ToolInputGuardrailException extends GuardrailException {

    private final String arguments;

    /**
     * Creates the exception with a message.
     *
     * @param message the message
     */
    public ToolInputGuardrailException(String message) {
        super(message);
        this.arguments = null;
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public ToolInputGuardrailException(String message, Throwable cause) {
        this(message, cause, null);
    }

    private ToolInputGuardrailException(String message, Throwable cause, String arguments) {
        super(message, cause);
        this.arguments = arguments;
    }

    /**
     * Gets the tool arguments that the failing guardrail validated: the arguments of the tool call after the
     * rewrites of the guardrails that ran before it (the arguments the LLM sent if none of them rewrote them).
     * <p>
     *     The tool was not executed, but the framework reports the rejected call with these arguments, as it does for
     *     a non-fatal failure (see {@link ToolInputGuardrailResult#rewrittenArguments()}).
     * </p>
     *
     * @return the validated tool arguments (a JSON string), or empty if the exception was not thrown by a
     *         {@link ToolInputGuardrailExecutor}
     */
    public Optional<String> arguments() {
        return Optional.ofNullable(arguments);
    }

    /**
     * A copy of this exception that carries the validated arguments, with the same message, cause, stack trace and
     * suppressed exceptions, for the executor to throw instead of this one.
     */
    ToolInputGuardrailException withArguments(String arguments) {
        var copy = new ToolInputGuardrailException(getMessage(), getCause(), arguments);
        copy.setStackTrace(getStackTrace());

        for (Throwable suppressed : getSuppressed()) {
            copy.addSuppressed(suppressed);
        }
        return copy;
    }
}
