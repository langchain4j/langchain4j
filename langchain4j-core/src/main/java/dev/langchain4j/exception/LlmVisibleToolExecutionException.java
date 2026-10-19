package dev.langchain4j.exception;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * A {@link ToolExecutionException} whose message is written for the LLM, so it implements
 * {@link ToolErrorVisibleToLlm}: error handlers that honor that interface send the message to the LLM
 * instead of failing the AI Service invocation.
 * <p>
 * It is thrown by tools that LangChain4j provides (for example, skills and tool search) when the LLM
 * can do something about the failure, typically when it called the tool with a missing or invalid argument.
 * <p>
 * To tell the LLM about a failure in your own {@code @Tool} method, throw
 * {@link ToolErrorVisibleToLlm#from(String)} or your own exception implementing {@link ToolErrorVisibleToLlm}.
 *
 * @since 1.21.0
 */
public class LlmVisibleToolExecutionException extends ToolExecutionException implements ToolErrorVisibleToLlm {

    /**
     * @param message the text to send to the LLM, written for the LLM. Must not be blank.
     */
    public LlmVisibleToolExecutionException(String message) {
        // (Throwable) null on purpose: ToolExecutionException(String) synthesises a RuntimeException cause,
        // which would show up as the cause of an error that has none
        super(ensureNotBlank(message, "message"), (Throwable) null);
    }

    /**
     * @param message the text to send to the LLM, written for the LLM. Must not be blank.
     * @param cause   the original error. It is not sent to the LLM.
     */
    public LlmVisibleToolExecutionException(String message, Throwable cause) {
        super(ensureNotBlank(message, "message"), cause);
    }

    @Override
    public String messageForLlm() {
        return getMessage();
    }
}
