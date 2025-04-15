package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.exception.LangChain4jException;

public class AnthropicHttpException extends LangChain4jException {

    private final Integer statusCode;

    public AnthropicHttpException(Integer statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * HTTP response status code. Can be {@code null}.
     */
    public Integer statusCode() {
        return statusCode;
    }
}
