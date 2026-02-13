package dev.langchain4j.model.anthropic.internal.api;

public class AnthropicStreamingErrorException extends RuntimeException {

    private final String type;

    public AnthropicStreamingErrorException(String message, String type) {
        super(message);
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
