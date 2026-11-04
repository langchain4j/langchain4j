package dev.langchain4j.mcp.client;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A request or notification that the MCP server sent to the client, as it was received.
 *
 * @param method the JSON-RPC method of the message, for example {@code notifications/resources/updated}
 * @param id the JSON-RPC ID of the message if it's a request, as the server sent it (a {@link String} or a
 *           {@link Number}), {@code null} if it's a notification
 * @param params the parameters of the message, empty if it has none
 */
public record McpServerMessageContext(
        String method, @Nullable Object id, Map<String, Object> params) {

    /**
     * The {@code _meta} of the message, which can carry, for example, the trace context of the server.
     *
     * @return the {@code _meta} of the message, empty if it has none
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> meta() {
        return params.get("_meta") instanceof Map<?, ?> meta ? (Map<String, Object>) meta : Map.of();
    }
}
