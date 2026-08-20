package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.mcp.client.transport.McpRawJson;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A business exception raised over the MCP protocol
 */
public class McpException extends LangChain4jException {

    private final int errorCode;
    private final String errorMessage;
    private final @Nullable String errorDataAsJson;

    public McpException(int errorCode, String errorMessage) {
        this(errorCode, errorMessage, (String) null);
    }

    /**
     * @param errorDataAsJson the JSON-RPC {@code error.data} member as raw JSON text.
     */
    public McpException(int errorCode, String errorMessage, @Nullable String errorDataAsJson) {
        super("Code: %d, message: %s".formatted(errorCode, errorMessage));
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorDataAsJson = errorDataAsJson;
    }

    /**
     * @deprecated use {@link #McpException(int, String, String)}, which does not expose Jackson types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public McpException(int errorCode, String errorMessage, @Nullable JsonNode errorData) {
        this(errorCode, errorMessage, errorData == null ? null : errorData.toString());
    }

    public int errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    /**
     * Returns the JSON-RPC {@code error.data} member as raw JSON text.
     */
    public @Nullable String errorDataAsJson() {
        return errorDataAsJson;
    }

    /**
     * Returns the JSON-RPC {@code error.data} member as plain values, so callers do not need a
     * JSON library.
     */
    public @Nullable Map<String, Object> errorDataAsMap() {
        return errorDataAsJson == null ? null : McpRawJson.toMap(errorDataAsJson);
    }

    /**
     * @deprecated use {@link #errorDataAsMap()} or {@link #errorDataAsJson()}.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public @Nullable JsonNode errorData() {
        return errorDataAsJson == null ? null : McpRawJson.parse(errorDataAsJson);
    }
}
