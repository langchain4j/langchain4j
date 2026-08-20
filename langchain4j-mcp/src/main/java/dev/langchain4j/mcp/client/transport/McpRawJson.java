package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Bridges between raw JSON text and Jackson's tree model while both the
 * {@code JsonNode}-based and the raw-JSON transport APIs coexist.
 */
@Internal
public final class McpRawJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McpRawJson() {}

    /**
     * Deserializes an MCP message into a protocol type.
     */
    public static <T> T deserialize(JsonNode message, Class<T> type) {
        return OBJECT_MAPPER.convertValue(message, type);
    }

    /**
     * Reads a JSON object as plain values.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String rawJson) {
        try {
            return OBJECT_MAPPER.readValue(rawJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse MCP message: " + rawJson, e);
        }
    }

    /**
     * Re-reads an already-decoded JSON value as the given type.
     */
    public static <T> T convert(Object value, Type type) {
        return OBJECT_MAPPER.convertValue(value, OBJECT_MAPPER.constructType(type));
    }

    /**
     * Serializes an outbound MCP message.
     */
    public static String serialize(Object message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize MCP message", e);
        }
    }

    public static JsonNode parse(String rawJson) {
        if (rawJson == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(rawJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse MCP message: " + rawJson, e);
        }
    }

    public static String toRawJson(JsonNode node) {
        return node == null ? null : node.toString();
    }

    /**
     * Maps a future's value while keeping cancellation linked to the source. A plain
     * {@code thenApply} would not propagate cancellation upstream, which would leave the
     * transport's response stream open after the client cancels the operation.
     */
    public static <T, R> CompletableFuture<R> map(CompletableFuture<T> source, Function<T, R> mapper) {
        CompletableFuture<R> mapped = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                source.cancel(mayInterruptIfRunning);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        source.whenComplete((value, error) -> {
            if (error != null) {
                mapped.completeExceptionally(error);
            } else {
                try {
                    mapped.complete(mapper.apply(value));
                } catch (Throwable t) {
                    mapped.completeExceptionally(t);
                }
            }
        });
        return mapped;
    }
}
