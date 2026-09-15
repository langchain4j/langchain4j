package dev.langchain4j.service.tool;

import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.AiServices;
import java.util.function.Function;

/**
 * Handler for {@link ToolExecutionException}s thrown by a {@link ToolExecutor}.
 * <p>
 * There are two ways to handle errors:
 * <p>
 * 1. Return a {@link ToolErrorHandlerResult#text(String) text message} that will be sent back
 * to the LLM, allowing it to respond appropriately (for example, by correcting the error and retrying).
 * <p>
 * 2. Throw an exception: this will stop the AI service flow.
 * Use {@link ToolErrorContext#rawError()} to access the raw error before cause-unwrapping
 * when deciding whether to throw.
 *
 * @see ToolArgumentsErrorHandler
 * @see AiServices#hallucinatedToolNameStrategy(Function)
 * @since 1.4.0
 */
@FunctionalInterface
public interface ToolExecutionErrorHandler {

    /**
     * Handles an error that occurred during tool execution.
     * <p>
     * This method should either throw an exception or return a {@link ToolErrorHandlerResult#text(String)},
     * which will be sent to the LLM as the result of the tool execution.
     *
     * @param error   The actual error that occurred (cause-unwrapped).
     *                Use {@link ToolErrorContext#rawError()} for the error before unwrapping.
     * @param context The context in which the error occurred.
     * @return The result of error handling.
     */
    ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context);

    /**
     * Returns a handler that rethrows the error, failing the AI Service invocation.
     * Nothing about the error is sent to the LLM.
     * <p>
     * Use this when a failing tool means the interaction cannot produce a correct answer,
     * or when the error must not reach the LLM.
     *
     * @since 1.21.0
     */
    static ToolExecutionErrorHandler failInvocation() {
        return (error, context) -> {
            throw ToolErrors.asRuntimeException(error);
        };
    }

    /**
     * Returns a handler that sends the message of the error to the LLM as the result of the tool execution,
     * giving the LLM a chance to react to it (for example, by trying another tool or by telling the user
     * what went wrong). The AI Service invocation continues.
     * <p>
     * <b>WARNING: this option can expose sensitive data.</b>
     * The message of an exception is usually written for developers, not for the LLM: it can contain
     * internal application details (file paths, SQL, credentials embedded in error strings, responses of
     * downstream services, personal data). Everything sent here reaches the LLM provider, is stored in the
     * chat memory and can end up in the answer the user reads, in logs and in observability pipelines.
     * <p>
     * Use this only when you know that the exception messages of all your tools are written with that in mind.
     * Otherwise, prefer a handler that returns a generic or sanitized description of the failure,
     * and keep the details in your logs.
     * <p>
     * An error implementing {@link ToolErrorVisibleToLlm} is an exception: for those, the text written in
     * {@link ToolErrorVisibleToLlm#messageForLlm()} is sent instead of the message of the exception.
     *
     * @since 1.21.0
     */
    static ToolExecutionErrorHandler sendExceptionMessageToLlm() {
        return (error, context) -> {
            ToolErrorHandlerResult authored = ToolErrors.messageForLlm(error, context);
            return authored != null ? authored : ToolErrorHandlerResult.text(ToolErrors.errorText(error));
        };
    }

    /**
     * Returns a handler that sends {@link ToolErrorVisibleToLlm#messageForLlm()} to the LLM when the tool
     * threw an exception implementing {@link ToolErrorVisibleToLlm}, and fails the AI Service invocation
     * for every other exception.
     * <p>
     * This lets the application decide, per exception, what the LLM is allowed to see:
     * a failure the LLM can do something about (for example, "there is no order with this ID")
     * is described to it in your own words, while an unexpected failure (a bug, a database that is down)
     * fails the invocation instead of being hidden from you.
     * <p>
     * If {@link ToolErrorVisibleToLlm#messageForLlm()} returns a blank text,
     * the AI Service invocation fails as well.
     *
     * @see ToolErrorVisibleToLlm
     * @since 1.21.0
     */
    static ToolExecutionErrorHandler failInvocationUnlessVisibleToLlm() {
        return (error, context) -> {
            ToolErrorHandlerResult result = ToolErrors.messageForLlm(error, context);
            if (result == null) {
                throw ToolErrors.asRuntimeException(error);
            }
            return result;
        };
    }

}
