package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.exception.ToolErrorVisibleToLlm;

/**
 * Shared logic of the ready-made {@link ToolExecutionErrorHandler}s and {@link ToolArgumentsErrorHandler}s.
 */
final class ToolErrors {

    private ToolErrors() {}

    /**
     * The message of the error, falling back to the type of the error when it has no message,
     * so that the LLM never receives an empty tool result.
     */
    static String errorText(Throwable error) {
        return isNullOrBlank(error.getMessage()) ? error.getClass().getName() : error.getMessage();
    }

    /**
     * The result to send to the LLM when the error decides itself what the LLM should see, or {@code null}
     * when it does not.
     * <p>
     * The error handed to a handler is cause-unwrapped, so the marked exception may be the one that was
     * originally thrown rather than the one the handler receives; both are inspected.
     */
    static ToolErrorHandlerResult messageForLlm(Throwable error, ToolErrorContext context) {
        ToolErrorHandlerResult result = messageForLlm(error);
        if (result != null) {
            return result;
        }
        return context == null ? null : messageForLlm(context.rawError());
    }

    private static ToolErrorHandlerResult messageForLlm(Throwable error) {
        if (error instanceof ToolErrorVisibleToLlm visibleError) {
            String message = visibleError.messageForLlm();
            if (!isNullOrBlank(message)) {
                return ToolErrorHandlerResult.text(message);
            }
        }
        return null;
    }

    static RuntimeException asRuntimeException(Throwable error) {
        return error instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(error);
    }
}
