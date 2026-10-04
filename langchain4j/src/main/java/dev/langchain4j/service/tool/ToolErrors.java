package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared logic of the ready-made {@link ToolExecutionErrorHandler}s and {@link ToolArgumentsErrorHandler}s.
 */
final class ToolErrors {

    private static final Logger log = LoggerFactory.getLogger(ToolErrors.class);

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
        if (context == null || context.rawError() == error) {
            return null;
        }
        return messageForLlm(context.rawError());
    }

    private static ToolErrorHandlerResult messageForLlm(Throwable error) {
        if (error instanceof ToolErrorVisibleToLlm visibleError) {
            String message = visibleError.messageForLlm();
            if (!isNullOrBlank(message)) {
                return ToolErrorHandlerResult.text(message);
            }
            log.warn(
                    "{} implements ToolErrorVisibleToLlm, but its messageForLlm() is blank, "
                            + "so it is handled as if it did not implement ToolErrorVisibleToLlm",
                    error.getClass().getName());
        }
        return null;
    }

    static void logErrorHiddenFromLlm(Throwable error, ToolErrorContext context) {
        String toolName = context == null ? null : context.toolExecutionRequest().name();
        log.warn("Tool '{}' failed. A generic message was sent to the LLM instead of the error", toolName, error);
    }

    static RuntimeException asRuntimeException(Throwable error) {
        return error instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(error);
    }
}
