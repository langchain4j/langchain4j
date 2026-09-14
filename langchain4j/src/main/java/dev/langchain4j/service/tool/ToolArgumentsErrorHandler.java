package dev.langchain4j.service.tool;

import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import dev.langchain4j.service.AiServices;
import java.util.function.Function;

/**
 * Handler for {@link ToolArgumentsException}s thrown by a {@link ToolExecutor}.
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
 * @see ToolExecutionErrorHandler
 * @see AiServices#hallucinatedToolNameStrategy(Function)
 * @since 1.4.0
 */
@FunctionalInterface
public interface ToolArgumentsErrorHandler {

    // There is deliberately no sendExceptionMessageToLlmFor(Class...) here, unlike on
    // ToolExecutionErrorHandler: that one exists for exceptions thrown by libraries a tool calls, which
    // the application cannot change. Argument errors are raised by LangChain4j itself while preparing the
    // arguments, so there is no third-party exception type to list.

    /**
     * Handles an error that occurred during the parsing and preparation of tool arguments.
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
     *
     * @since 1.21.0
     */
    static ToolArgumentsErrorHandler failInvocation() {
        return (error, context) -> {
            throw ToolErrors.asRuntimeException(error);
        };
    }

    /**
     * Returns a handler that sends {@link ToolErrorVisibleToLlm#messageForLlm()} to the LLM when the error
     * implements {@link ToolErrorVisibleToLlm}, and fails the AI Service invocation for every other error.
     * <p>
     * Most argument errors are produced by LangChain4j itself and do not implement that interface, so this
     * handler mainly matters when your own code takes part in preparing the arguments, for example
     * a custom deserializer or a validating tool parameter type.
     * <p>
     * If {@link ToolErrorVisibleToLlm#messageForLlm()} returns a blank text,
     * the AI Service invocation fails as well.
     *
     * @see ToolErrorVisibleToLlm
     * @since 1.21.0
     */
    static ToolArgumentsErrorHandler failInvocationUnlessVisibleToLlm() {
        return (error, context) -> {
            ToolErrorHandlerResult result = ToolErrors.messageForLlm(error, context);
            if (result == null) {
                throw ToolErrors.asRuntimeException(error);
            }
            return result;
        };
    }

    /**
     * Returns a handler that sends the message of the error to the LLM as the result of the tool execution,
     * so that the LLM can correct the arguments and call the tool again. The AI Service invocation continues.
     * <p>
     * Argument errors usually originate from the LLM (malformed JSON, a missing field, a wrong type),
     * and LLMs can typically fix them once they see what went wrong.
     * <p>
     * <b>WARNING: this option can expose sensitive data.</b>
     * Most argument errors are produced by LangChain4j and describe the arguments the LLM itself generated,
     * but an error can also come from your own code (for example, from a custom deserializer or a validation
     * check inside a tool parameter type). Such a message reaches the LLM provider, is stored in the chat
     * memory and can end up in the answer the user reads.
     * <p>
     * An error implementing {@link ToolErrorVisibleToLlm} is an exception: for those, the text written in
     * {@link ToolErrorVisibleToLlm#messageForLlm()} is sent instead of the message of the exception.
     *
     * @since 1.21.0
     */
    static ToolArgumentsErrorHandler sendExceptionMessageToLlm() {
        return (error, context) -> {
            ToolErrorHandlerResult authored = ToolErrors.messageForLlm(error, context);
            return authored != null ? authored : ToolErrorHandlerResult.text(ToolErrors.errorText(error));
        };
    }
}
