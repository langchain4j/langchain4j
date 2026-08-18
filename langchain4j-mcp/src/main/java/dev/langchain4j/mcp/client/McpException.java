package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.exception.LangChain4jException;
import org.jspecify.annotations.Nullable;

/**
 * A business exception raised over the MCP protocol
 */
public class McpException extends LangChain4jException {

    private final int errorCode;
    private final String errorMessage;
    private final @Nullable JsonNode errorData;

    public McpException(int errorCode, String errorMessage) {
        this(errorCode, errorMessage, null);
    }

    public McpException(int errorCode, String errorMessage, @Nullable JsonNode errorData) {
        super("Code: %d, message: %s".formatted(errorCode, errorMessage));
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorData = errorData;
    }

    public int errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public @Nullable JsonNode errorData() {
        return errorData;
    }
}
