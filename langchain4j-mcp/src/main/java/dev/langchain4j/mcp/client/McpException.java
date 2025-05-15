package dev.langchain4j.mcp.client;

import dev.langchain4j.exception.LangChain4jException;

/**
 * A business exception raised over the MCP protocol
 */
public class McpException extends LangChain4jException {

    private final int errorCode;
    private final String errorMessage;

    public McpException(int errorCode, String errorMessage) {
        super("Code: %d, message: %s".formatted(errorCode, errorMessage));
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public int errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
