package dev.langchain4j.mcp.client;

/**
 * A business exception raised over the MCP protocol
 */
public class McpException extends RuntimeException {

    private final int errorCode;
    private final String errorMessage;

    public McpException(int errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String getMessage() {
        return "Code: " + errorCode + ", message: " + errorMessage;
    }
}
