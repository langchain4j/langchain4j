package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.LangChain4jException;

/**
 * Thrown when a {@link ToolGuardrail} fails fatally, or when a guardrail itself throws.
 * <p>
 * Unlike a non-fatal failure, this is never converted into an error result for the LLM: it propagates out
 * of the AI Service invocation and is deliberately not passed to the
 * {@link dev.langchain4j.service.tool.ToolExecutionErrorHandler}, which exists to help the model recover
 * from tool errors. A fatal guardrail failure is a decision that the model must <em>not</em> recover from.
 * <p>
 * It extends {@link LangChain4jException} rather than {@link dev.langchain4j.guardrail.GuardrailException}
 * because the latter is sealed to the guardrails package in {@code langchain4j-core}, and the tool
 * guardrail API lives in the {@code langchain4j} module alongside {@code ToolExecutionResult}.
 *
 * @since 1.19.0
 */
@Experimental
public class ToolGuardrailException extends LangChain4jException {

    private final boolean fatal;

    public ToolGuardrailException(String message) {
        this(message, null, true);
    }

    public ToolGuardrailException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public ToolGuardrailException(String message, Throwable cause, boolean fatal) {
        super(message, cause);
        this.fatal = fatal;
    }

    /**
     * Whether the guardrail failure was fatal. Always {@code true} for exceptions thrown by LangChain4j
     * itself; the flag exists so that downstream frameworks can reuse this type for their own signalling.
     */
    public boolean isFatal() {
        return fatal;
    }
}
