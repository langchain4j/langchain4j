package dev.langchain4j.mcp.client;

import dev.langchain4j.exception.LangChain4jException;

public class IllegalResponseException extends LangChain4jException {

    public IllegalResponseException(String message) {
        super(message);
    }
}
