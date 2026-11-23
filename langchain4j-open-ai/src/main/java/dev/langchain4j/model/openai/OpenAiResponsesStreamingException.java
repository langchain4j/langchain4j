package dev.langchain4j.model.openai;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Passed to {@code onError} when a Responses API stream reports a failure through a
 * {@code response.failed}, {@code response.error} or {@code error} event.
 * {@link #code()} returns the error code from the event, or {@code null} when it carries none.
 */
public class OpenAiResponsesStreamingException extends LangChain4jException {

    private final String code;

    public OpenAiResponsesStreamingException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
