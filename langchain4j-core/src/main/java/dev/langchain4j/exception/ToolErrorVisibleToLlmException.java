package dev.langchain4j.exception;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * A ready-made {@link ToolErrorVisibleToLlm} exception: throw it from a {@code @Tool} method
 * when you want to tell the LLM what went wrong without declaring an exception class of your own.
 * <p>
 * Usually created via {@link ToolErrorVisibleToLlm#of(String)} or
 * {@link ToolErrorVisibleToLlm#of(String, Throwable)}.
 *
 * @since 1.21.0
 */
public class ToolErrorVisibleToLlmException extends RuntimeException implements ToolErrorVisibleToLlm {

    public ToolErrorVisibleToLlmException(String message) {
        super(ensureNotBlank(message, "message"));
    }

    public ToolErrorVisibleToLlmException(String message, Throwable cause) {
        super(ensureNotBlank(message, "message"), cause);
    }

    @Override
    public String messageForLlm() {
        return getMessage();
    }
}
