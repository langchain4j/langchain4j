package dev.langchain4j.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrailResult.Failure;

/**
 * A tool input guardrail is a rule applied before a tool is executed, to validate or rewrite the arguments the LLM chose.
 * <p>
 *     Unlike {@link InputGuardrail}, it has no retry or reprompt: a non-fatal failure becomes an error tool result
 *     sent to the LLM, which may then correct itself.
 * </p>
 * <p>
 *     A non-fatal failure does not stop the other guardrails of the chain from running; their failures are
 *     aggregated. A fatal failure stops the chain and aborts the AI service invocation.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public interface ToolInputGuardrail extends Guardrail<ToolInputGuardrailRequest, ToolInputGuardrailResult> {
    /**
     * Validates the tool execution request before the tool is executed.
     * <p>
     *     Implementations must not modify the request.
     * </p>
     *
     * @param request
     *            the request
     * @return the result of the validation
     */
    @Override
    ToolInputGuardrailResult validate(ToolInputGuardrailRequest request);

    /**
     * Produces a successful result without any rewrite.
     *
     * @return the result of a successful tool input guardrail validation
     */
    default ToolInputGuardrailResult success() {
        return ToolInputGuardrailResult.success();
    }

    /**
     * Produces a successful result with rewritten tool arguments, which replace the original ones for the
     * following guardrails and for the tool execution.
     *
     * @param rewrittenArguments
     *            the new arguments of the tool, as a JSON string
     * @return the result of a successful tool input guardrail validation carrying the rewritten arguments
     */
    default ToolInputGuardrailResult successWith(String rewrittenArguments) {
        return ToolInputGuardrailResult.successWith(rewrittenArguments);
    }

    /**
     * Produces a non-fatal failure. The tool is not executed and the message is sent to the LLM
     * <strong>verbatim</strong> as the content of an error tool result, so it is fully under your control.
     * <p>
     *     Do not quote the checked content in the message: that would send it to the LLM anyway.
     * </p>
     *
     * @param message
     *            the message describing the failure, sent to the LLM
     * @return the result of a failed tool input guardrail validation
     */
    default ToolInputGuardrailResult failure(String message) {
        return new ToolInputGuardrailResult(new Failure(message), false);
    }

    /**
     * Produces a non-fatal failure, see {@link #failure(String)}.
     *
     * @param message
     *            the message describing the failure, sent to the LLM
     * @param cause
     *            the exception that caused this failure; it is not sent to the LLM
     * @return the result of a failed tool input guardrail validation
     */
    default ToolInputGuardrailResult failure(String message, Throwable cause) {
        return new ToolInputGuardrailResult(new Failure(message, cause), false);
    }

    /**
     * Produces a fatal failure. It stops the other guardrails and aborts the AI service invocation with a
     * {@link ToolInputGuardrailException}; the LLM does not get a chance to retry.
     * Use it when retrying cannot help, e.g. a rejected page that would come back identical.
     *
     * @param message
     *            the message describing the failure
     * @return the result of a fatally failed tool input guardrail validation
     */
    default ToolInputGuardrailResult fatal(String message) {
        return new ToolInputGuardrailResult(new Failure(message), true);
    }

    /**
     * Produces a fatal failure, see {@link #fatal(String)}.
     *
     * @param message
     *            the message describing the failure
     * @param cause
     *            the exception that caused this failure
     * @return the result of a fatally failed tool input guardrail validation
     */
    default ToolInputGuardrailResult fatal(String message, Throwable cause) {
        return new ToolInputGuardrailResult(new Failure(message, cause), true);
    }
}
