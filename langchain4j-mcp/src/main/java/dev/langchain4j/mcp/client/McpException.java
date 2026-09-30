package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.mcp.client.transport.McpJson;
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
     * Creates an exception carrying the JSON-RPC {@code error.data} member as JSON text.
     *
     * <p>This is a factory rather than a constructor so that {@code new McpException(code, message, null)}
     * keeps resolving to a single constructor and continues to compile.
     */
    public static McpException withErrorData(int errorCode, String errorMessage, @Nullable String errorDataAsJson) {
        return new McpException(errorCode, errorMessage, errorDataAsJson);
    }

    private McpException(int errorCode, String errorMessage, @Nullable String errorDataAsJson) {
        super("Code: %d, message: %s".formatted(errorCode, errorMessage));
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorDataAsJson = errorDataAsJson;
    }

    /**
     * @deprecated use {@link #withErrorData(int, String, String)}, which does not expose Jackson types.
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
     * Returns the JSON-RPC {@code error.data} member as JSON text.
     */
    public @Nullable String errorDataAsJson() {
        return errorDataAsJson;
    }

    /**
     * Returns the JSON-RPC {@code error.data} member as plain values, so callers do not need a
     * JSON library.
     *
     * @return null when there is no {@code error.data}, and also when it is not a JSON object -
     * JSON-RPC allows any value there. Use {@link #errorDataAsObject()} to read those.
     */
    public @Nullable Map<String, Object> errorDataAsMap() {
        Object data = errorDataAsObject();
        return data instanceof Map ? asMap(data) : null;
    }

    /**
     * Returns the JSON-RPC {@code error.data} member as a plain JDK value: a {@link Map}, a
     * {@link java.util.List}, a boxed primitive, or null. JSON-RPC allows {@code error.data} to be
     * any value, so this is the accessor that can represent all of them.
     */
    public @Nullable Object errorDataAsObject() {
        return errorDataAsJson == null ? null : McpJson.toValue(errorDataAsJson);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    /**
     * @deprecated use {@link #errorDataAsMap()} or {@link #errorDataAsJson()}.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public @Nullable JsonNode errorData() {
        return errorDataAsJson == null ? null : McpJson.parse(errorDataAsJson);
    }
}
