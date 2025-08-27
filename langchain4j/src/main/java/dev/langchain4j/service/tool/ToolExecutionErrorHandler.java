package dev.langchain4j.service.tool;

import java.util.function.Function;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.AiServices;

/**
 * Handler for {@link ToolExecutionException}s thrown by a {@link ToolExecutor}.
 * <p>
 * Currently, there are two ways to handle errors:
 * <p>
 * 1. Throw an exception: this will stop the AI service flow.
 * <p>
 * 2. Return a text message (e.g., an error description) that will be sent back to the LLM,
 * allowing it to respond appropriately (for example, by correcting the error and retrying).
 *
 * @see ToolExecutionErrorHandler
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
     * @param error   The actual error that occurred.
     * @param context The context in which the error occurred.
     * @return The result of error handling.
     */
    ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context);
}
