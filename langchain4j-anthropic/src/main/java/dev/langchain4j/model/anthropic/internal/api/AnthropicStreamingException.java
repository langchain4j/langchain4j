package dev.langchain4j.model.anthropic.internal.api;

import dev.langchain4j.exception.LangChain4jException;

public class AnthropicStreamingException extends LangChain4jException {

    private final String type;

    public AnthropicStreamingException(String message, String type) {
        super(message);
        this.type = type;
    }

    public String type() {
        return type;
    }
}
