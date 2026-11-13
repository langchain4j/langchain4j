package dev.langchain4j.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult.Failure;
import java.util.List;

/**
 * A tool output guardrail is a rule applied after a tool was executed, to validate or rewrite the result before the LLM sees it.
 * <p>
 *     Unlike {@link OutputGuardrail}, it has no retry or reprompt: a non-fatal failure becomes an error tool result
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
public interface ToolOutputGuardrail extends Guardrail<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> {
    /**
     * Validates the result of a tool execution, including error results ({@link ToolOutputGuardrailRequest#isError()}).
     * <p>
     *     Implementations must not modify the request.
     * </p>
     *
     * @param request
     *            the request
     * @return the result of the validation
     */
    @Override
    ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request);

    /**
     * Produces a successful result without any rewrite.
     *
     * @return the result of a successful tool output guardrail validation
     */
    default ToolOutputGuardrailResult success() {
        return ToolOutputGuardrailResult.success();
    }

    /**
     * Produces a successful result with rewritten text: the text contents of the tool result are replaced, the
     * other contents and the error flag are kept.
     *
     * @param rewrittenText
     *            the new text of the tool result
     * @return the result of a successful tool output guardrail validation carrying the rewritten text
     */
    default ToolOutputGuardrailResult successWith(String rewrittenText) {
        return ToolOutputGuardrailResult.successWith(rewrittenText);
    }

    /**
     * Produces a successful result that replaces all the contents of the tool result and its error flag.
     * <p>
     *     Passing {@code isError == true} turns the result into an error result, which counts as a failed step
     *     for compensation purposes.
     * </p>
     *
     * @param rewrittenContents
     *            the new contents of the tool result
     * @param isError
     *            whether the rewritten tool result is an error
     * @return the result of a successful tool output guardrail validation carrying the rewritten contents
     */
    default ToolOutputGuardrailResult successWith(List<Content> rewrittenContents, boolean isError) {
        return ToolOutputGuardrailResult.successWith(rewrittenContents, isError);
    }

    /**
     * Produces a non-fatal failure. The tool result is replaced by an error tool result, and the message is sent to the LLM
     * <strong>verbatim</strong> as the content of an error tool result, so it is fully under your control.
     * <p>
     *     Do not quote the checked content in the message: that would send it to the LLM anyway.
     * </p>
     *
     * @param message
     *            the message describing the failure, sent to the LLM
     * @return the result of a failed tool output guardrail validation
     */
    default ToolOutputGuardrailResult failure(String message) {
        return new ToolOutputGuardrailResult(new Failure(message), false);
    }

    /**
     * Produces a non-fatal failure, see {@link #failure(String)}.
     *
     * @param message
     *            the message describing the failure, sent to the LLM
     * @param cause
     *            the exception that caused this failure; it is not sent to the LLM
     * @return the result of a failed tool output guardrail validation
     */
    default ToolOutputGuardrailResult failure(String message, Throwable cause) {
        return new ToolOutputGuardrailResult(new Failure(message, cause), false);
    }

    /**
     * Produces a fatal failure. It stops the other guardrails and aborts the AI service invocation with a
     * {@link ToolOutputGuardrailException}; the LLM does not get a chance to retry.
     * Use it when retrying cannot help, e.g. a rejected page that would come back identical.
     *
     * @param message
     *            the message describing the failure
     * @return the result of a fatally failed tool output guardrail validation
     */
    default ToolOutputGuardrailResult fatal(String message) {
        return new ToolOutputGuardrailResult(new Failure(message), true);
    }

    /**
     * Produces a fatal failure, see {@link #fatal(String)}.
     *
     * @param message
     *            the message describing the failure
     * @param cause
     *            the exception that caused this failure
     * @return the result of a fatally failed tool output guardrail validation
     */
    default ToolOutputGuardrailResult fatal(String message, Throwable cause) {
        return new ToolOutputGuardrailResult(new Failure(message, cause), true);
    }
}
