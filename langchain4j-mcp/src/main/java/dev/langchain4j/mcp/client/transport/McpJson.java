package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.JsonException;
import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
     * Reads and writes MCP payloads through the pluggable wire codec, so that the JSON library
     * backing the MCP client can be swapped - which is the point of the opt-in Jackson 3 module.
     *
     * <p>Wire codecs ignore unknown properties, which MCP needs: the protocol adds fields in a
     * backwards-compatible way, such as the {@code title} that 2025-06-18 put on every named object.
     */
    private static final Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder().build());

    /**
     * Only for the tree the deprecated {@code JsonNode} APIs still expose, and for the tree the
     * client itself carries between transport and parsing. Both go when those APIs do.
     */
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private McpJson() {}

    /**
     * Deserializes an MCP message into a protocol type.
     *
     * <p>The message is taken as it arrived on the wire, so this is a single parse. Reading it into
     * a tree first and then into a type costs two more passes and, for high-precision numbers, is
     * not guaranteed to round-trip.
     */
    public static <T> T deserialize(String message, Class<T> type) {
        return read(message, type);
    }

    /**
     * Deserializes an MCP message held as a Jackson 2 tree.
     *
     * @deprecated use {@link #deserialize(String, Class)}, which does not expose Jackson types and
     * avoids re-serializing the tree in order to read it.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public static <T> T deserialize(JsonNode message, Class<T> type) {
        return read(message.toString(), type);
    }

    /**
     * Reads any JSON value as plain JDK types: a {@link Map}, a {@link java.util.List}, a boxed
     * primitive, or null for a JSON null.
     *
     * @throws JsonReadException if the text is not valid JSON.
     */
    public static Object toValue(String json) {
        return read(json, Object.class);
    }

    /**
     * Reads a JSON object as plain values.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        return read(json, Map.class);
    }

    /**
     * Re-reads an already-decoded JSON value as the given type.
     */
    public static <T> T convert(Object value, Class<T> type) {
        return read(serialize(value), type);
    }

    /**
     * Re-reads an already-decoded JSON array as a list of the given element type.
     */
    public static <T> List<T> convertList(Object value, Class<T> elementType) {
        return read(serialize(value), listOf(elementType));
    }

    private static ParameterizedType listOf(Class<?> elementType) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[] {elementType};
            }

            @Override
            public Type getRawType() {
                return List.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    /**
     * Serializes an outbound MCP message. A null message renders as null rather than as the
     * JSON null literal, so that an absent payload stays absent.
     */
    public static String serialize(Object message) {
        if (message == null) {
            return null;
        }
        if (message instanceof JsonNode node) {
            // A Jackson 2 tree is a type only Jackson 2 knows. Another codec would not recognise it
            // and would write out its bean properties - {"empty":false,"nodeType":"OBJECT",...} -
            // rather than the JSON it holds. Its own toString() is that JSON.
            return node.toString();
        }
        try {
            return CODEC.toJson(message);
        } catch (JsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonWriteException("Failed to serialize MCP message", e);
        }
    }

    public static JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new JsonReadException("Failed to parse MCP message", e);
        }
    }

    /**
     * Maps a future's value while keeping cancellation linked to the source. A plain
     * {@code thenApply} would not propagate cancellation upstream, which would leave the
     * transport's response stream open after the client cancels the operation.
     */
    /**
     * Reads JSON text. The codecs already report a failure as {@link JsonReadException} whichever
     * JSON library is plugged in, which is what a transport branches on when deciding whether to
     * fail an operation or log and carry on; this adds which message could not be read.
     */
    private static <T> T read(String json, Class<T> type) {
        return read(json, (Type) type);
    }

    private static <T> T read(String json, Type type) {
        try {
            return CODEC.fromJson(json, type);
        } catch (JsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonReadException("Failed to parse MCP message", e);
        }
    }

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
