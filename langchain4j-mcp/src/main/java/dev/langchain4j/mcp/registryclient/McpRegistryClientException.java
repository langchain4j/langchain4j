package dev.langchain4j.mcp.registryclient;

import dev.langchain4j.exception.LangChain4jException;

public class McpRegistryClientException extends LangChain4jException {

    public McpRegistryClientException(String message) {
        super(message);
    }

    public McpRegistryClientException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public McpRegistryClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
