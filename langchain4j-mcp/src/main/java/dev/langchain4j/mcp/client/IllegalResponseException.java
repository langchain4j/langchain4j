package dev.langchain4j.mcp.client;

public class IllegalResponseException extends RuntimeException {

    public IllegalResponseException(String message) {
        super(message);
    }
}
