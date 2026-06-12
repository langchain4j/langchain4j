package dev.langchain4j.service.tool;

import java.util.function.Function;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.service.AiServices;

/**
 * Handler for {@link ToolArgumentsException}s thrown by a {@link ToolExecutor}.
 * <p>
 * There are three ways to handle errors:
 * <p>
 * 1. Return a {@link ToolErrorHandlerResult#text(String) text message} that will be sent back
 * to the LLM, allowing it to respond appropriately (for example, by correcting the error and retrying).
 * <p>
 * 2. Throw an exception: this will stop the AI service flow.
 * <p>
 * 3. Return {@link ToolErrorHandlerResult#propagateException()}: re-throws the original exception
 * out of the AI Service. Use {@link ToolErrorContext#originalException()} to inspect the original
 * exception (before cause-unwrapping) when deciding whether to propagate.
 *
 * @see ToolExecutionErrorHandler
 * @see AiServices#hallucinatedToolNameStrategy(Function)
 * @since 1.4.0
 */
@FunctionalInterface
public interface ToolArgumentsErrorHandler {

    /**
     * Handles an error that occurred during the parsing and preparation of tool arguments.
     * <p>
     * This method should either throw an exception, return a {@link ToolErrorHandlerResult#text(String)}
     * (which will be sent to the LLM as the result of the tool execution),
     * or return {@link ToolErrorHandlerResult#propagateException()} to re-throw the original exception.
     *
     * @param error   The actual error that occurred (cause-unwrapped).
     *                Use {@link ToolErrorContext#originalException()} for the original wrapper.
     * @param context The context in which the error occurred.
     * @return The result of error handling.
     */
    ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context);
}
