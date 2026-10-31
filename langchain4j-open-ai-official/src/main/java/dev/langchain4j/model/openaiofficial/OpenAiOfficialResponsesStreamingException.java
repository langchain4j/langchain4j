package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Passed to {@code onError} when a Responses API stream reports a failure through a
 * {@code response.failed} or {@code error} event.
 * {@link #code()} and {@link #type()} return the provider's error code and type,
 * or {@code null} when the event carries none.
 */
public class OpenAiOfficialResponsesStreamingException extends LangChain4jException {

    private final String code;
    private final String type;

    public OpenAiOfficialResponsesStreamingException(String message, String code, String type) {
        super(message);
        this.code = code;
        this.type = type;
    }

    public OpenAiOfficialResponsesStreamingException(String message, String code, String type, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.type = type;
    }

    public String code() {
        return code;
    }

    public String type() {
        return type;
    }
}
