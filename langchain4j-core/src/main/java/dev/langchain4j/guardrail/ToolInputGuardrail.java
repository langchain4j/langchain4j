package dev.langchain4j.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import java.util.Map;
import org.jspecify.annotations.Nullable;

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
 * <p>
 *     One instance can be shared by every tool and every invocation of an AI service, and called concurrently, so
 *     implementations must be thread-safe.
 * </p>
 *
 * @since 1.25.0
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
     * <p>
     *     The chat memory keeps the tool call as the LLM made it: the rewrite changes what the tool runs with and what
     *     the events report, not the history. The tool result that the LLM gets starts with a note that the arguments
     *     were changed (see {@link ToolInputGuardrailsConfig}). Pass values that only
     *     the tool needs through {@code InvocationParameters}, not through the arguments.
     * </p>
     *
     * @param rewrittenArguments
     *            the new arguments of the tool, as a JSON object. {@code null} means no rewrite (a plain
     *            success). Anything else that is not a JSON object (malformed JSON, an array, a number) is a fatal
     *            failure of the guardrail: it aborts the invocation with a
     *            {@link ToolInputGuardrailException}, before the tool runs.
     * @return the result of a successful tool input guardrail validation carrying the rewritten arguments
     */
    default ToolInputGuardrailResult successWith(@Nullable String rewrittenArguments) {
        return ToolInputGuardrailResult.successWith(rewrittenArguments);
    }

    /**
     * Produces a successful result with rewritten tool arguments, given as a map of argument names to values, which
     * replace the original ones for the following guardrails and for the tool execution. The map is serialized to a
     * JSON object with the same JSON codec that {@link ToolInputGuardrailRequest#argumentsAsMap()} parses the
     * arguments with, so a guardrail can read the arguments as a map, change them and return the map, without
     * escaping anything by hand:
     * <pre>{@code
     * var arguments = new HashMap<>(request.argumentsAsMap());
     * arguments.put("query", sanitize((String) arguments.get("query")));
     * return successWithArguments(arguments);
     * }</pre>
     * <p>
     *     The chat memory keeps the tool call as the LLM made it, as with {@link #successWith(String)}. Unlike
     *     {@link #successWith(String)}, a {@code null} map is rejected: a guardrail that redacts the arguments and
     *     returns {@code null} by mistake must not let the original arguments through. A {@code null} map, or a value
     *     that has no JSON representation, is a fatal failure of the guardrail: it aborts the invocation with a
     *     {@link ToolInputGuardrailException}, before the tool runs.
     * </p>
     *
     * @param rewrittenArguments the new arguments of the tool, by name; must not be {@code null}
     * @return the result of a successful tool input guardrail validation carrying the rewritten arguments
     */
    default ToolInputGuardrailResult successWithArguments(Map<String, ?> rewrittenArguments) {
        return ToolInputGuardrailResult.successWithArguments(rewrittenArguments);
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
