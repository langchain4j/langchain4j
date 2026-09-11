package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

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
    static ToolExecutionErrorHandler failAiServiceInvocation() {
        return (error, context) -> {
            throw asRuntimeException(error);
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
     *
     * @since 1.21.0
     */
    static ToolExecutionErrorHandler sendExceptionMessageToLlm() {
        return (error, context) -> ToolErrorHandlerResult.text(ToolService.errorText(error));
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
     * @see #sendExceptionMessageToLlmFor(Class[])
     * @since 1.21.0
     */
    static ToolExecutionErrorHandler failUnlessVisibleToLlm() {
        return (error, context) -> {
            ToolErrorHandlerResult result = messageForLlm(error);
            if (result == null) {
                throw asRuntimeException(error);
            }
            return result;
        };
    }

    /**
     * Returns a handler that behaves like {@link #failUnlessVisibleToLlm()}, but additionally sends the message
     * of the exception to the LLM when the exception is an instance of one of the given types (subtypes included).
     * <p>
     * Use it for exceptions you cannot change, for example those thrown by a library:
     * <pre>{@code
     * .toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlmFor(EntityNotFoundException.class))
     * }</pre>
     * <p>
     * <b>WARNING: for the given types, this option can expose sensitive data.</b>
     * The message of an exception is usually written for developers, not for the LLM, so list only types
     * whose messages you know to be safe for the LLM provider to see. Exceptions implementing
     * {@link ToolErrorVisibleToLlm} are not affected: for those, the text you wrote in
     * {@link ToolErrorVisibleToLlm#messageForLlm()} is sent instead.
     *
     * @param types the exception types whose message may be sent to the LLM. Must not be empty.
     * @see ToolErrorVisibleToLlm
     * @see #failUnlessVisibleToLlm()
     * @since 1.21.0
     */
    @SafeVarargs
    static ToolExecutionErrorHandler sendExceptionMessageToLlmFor(Class<? extends Throwable>... types) {
        Class<? extends Throwable>[] visibleTypes = ensureNotEmpty(types, "types").clone();
        for (Class<? extends Throwable> type : visibleTypes) {
            ensureNotNull(type, "type");
        }
        return (error, context) -> {
            ToolErrorHandlerResult result = messageForLlm(error);
            if (result != null) {
                return result;
            }
            for (Class<? extends Throwable> type : visibleTypes) {
                if (type.isInstance(error)) {
                    return ToolErrorHandlerResult.text(ToolService.errorText(error));
                }
            }
            throw asRuntimeException(error);
        };
    }

    /**
     * The result to send to the LLM when the error decides itself what the LLM should see,
     * or {@code null} when it does not.
     */
    private static ToolErrorHandlerResult messageForLlm(Throwable error) {
        if (error instanceof ToolErrorVisibleToLlm visibleError) {
            String message = visibleError.messageForLlm();
            if (!isNullOrBlank(message)) {
                return ToolErrorHandlerResult.text(message);
            }
        }
        return null;
    }

    private static RuntimeException asRuntimeException(Throwable error) {
        return error instanceof RuntimeException runtimeException
                ? runtimeException
                : new RuntimeException(error);
    }
}
