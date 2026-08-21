package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Bridges between JSON text and Jackson's tree model while both the
 * deprecated {@code JsonNode}-based transport API and its replacement coexist.
 */
@Internal
public final class McpJson {

    /**
     * A server may send fields this client does not model yet — MCP adds them in a backwards-compatible
     * way, such as the {@code title} that 2025-06-18 put on every named object — so unknown properties
     * are ignored rather than treated as errors.
     */
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private McpJson() {}

    /**
     * Deserializes an MCP message into a protocol type.
     */
    public static <T> T deserialize(JsonNode message, Class<T> type) {
        return OBJECT_MAPPER.convertValue(message, type);
    }

    /**
     * Reads any JSON value as plain JDK types: a {@link Map}, a {@link java.util.List}, a boxed
     * primitive, or null for a JSON null.
     *
     * @throws IllegalArgumentException if the text is not valid JSON.
     */
    public static Object toValue(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse MCP message: " + json, e);
        }
    }

    /**
     * Reads a JSON object as plain values.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse MCP message: " + json, e);
        }
    }

    /**
     * Re-reads an already-decoded JSON value as the given type.
     */
    public static <T> T convert(Object value, Class<T> type) {
        return OBJECT_MAPPER.convertValue(value, type);
    }

    /**
     * Re-reads an already-decoded JSON array as a list of the given element type.
     */
    public static <T> List<T> convertList(Object value, Class<T> elementType) {
        return OBJECT_MAPPER.convertValue(
                value, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    /**
     * Serializes an outbound MCP message. A null message renders as null rather than as the
     * JSON null literal, so that an absent payload stays absent.
     */
    public static String serialize(Object message) {
        if (message == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize MCP message", e);
        }
    }

    public static JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse MCP message: " + json, e);
        }
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
