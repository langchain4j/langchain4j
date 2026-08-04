package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps LangChain4j embeddings and text segments to the {@code vectors} JSON accepted by
 * {@code DBMS_VECTOR_DATABASE.UPSERT_VECTORS}.
 */
public final class VecDbVectorJsonMapper {

    static final String TEXT_METADATA_KEY = "text";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

    private VecDbVectorJsonMapper() {}

    /**
     * Maps embeddings without text or metadata to VecDB vector records.
     */
    public static String toJson(List<String> ids, List<Embedding> embeddings) {
        ensureNotNull(ids, "ids");
        ensureNotNull(embeddings, "embeddings");
        ensureSameSize(ids, "ids", embeddings, "embeddings");

        ArrayNode vectors = OBJECT_MAPPER.createArrayNode();
        for (int i = 0; i < ids.size(); i++) {
            vectors.add(toJsonObject(
                    ensureElementNotNull(ids, i, "ids"), ensureElementNotNull(embeddings, i, "embeddings")));
        }
        return vectors.toString();
    }

    /**
     * Maps embeddings, text, and metadata to VecDB vector records.
     */
    public static String toJson(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        ensureNotNull(ids, "ids");
        ensureNotNull(embeddings, "embeddings");
        ensureNotNull(segments, "segments");
        ensureSameSize(ids, "ids", embeddings, "embeddings");
        ensureSameSize(ids, "ids", segments, "segments");

        ArrayNode vectors = OBJECT_MAPPER.createArrayNode();
        for (int i = 0; i < ids.size(); i++) {
            String id = ensureElementNotNull(ids, i, "ids");
            Embedding embedding = ensureElementNotNull(embeddings, i, "embeddings");
            TextSegment segment = ensureElementNotNull(segments, i, "segments");

            ObjectNode vector = toJsonObject(id, embedding);
            vector.set("metadata", toMetadataJson(segment));
            vectors.add(vector);
        }
        return vectors.toString();
    }

    private static ObjectNode toJsonObject(String id, Embedding embedding) {
        ObjectNode vector = OBJECT_MAPPER.createObjectNode();
        vector.put("id", ensureNotBlank(id, "id"));

        ArrayNode denseVector = vector.putArray("dense_vector");
        for (float value : embedding.vector()) {
            denseVector.add(value);
        }
        return vector;
    }

    private static ObjectNode toMetadataJson(TextSegment segment) {
        Map<String, Object> metadata = segment.metadata().toMap();
        if (metadata.containsKey(TEXT_METADATA_KEY)) {
            throw new IllegalArgumentException(
                    "TextSegment metadata must not contain the reserved key \"" + TEXT_METADATA_KEY + "\"");
        }

        ObjectNode metadataJson = OBJECT_MAPPER.createObjectNode();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            JsonNode value = OBJECT_MAPPER.valueToTree(entry.getValue());
            metadataJson.set(entry.getKey(), value);
        }
        metadataJson.put(TEXT_METADATA_KEY, segment.text());
        return metadataJson;
    }

    public static String idsToJson(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        ArrayNode idsJson = OBJECT_MAPPER.createArrayNode();
        for (String id : ids) {
            idsJson.add(ensureNotBlank(id, "id"));
        }
        return idsJson.toString();
    }

    public static List<ListedVector> vectorsFromListResponse(String responseJson) {
        ensureNotBlank(responseJson, "responseJson");
        JsonNode response;
        try {
            response = OBJECT_MAPPER.readTree(responseJson);
        } catch (JsonProcessingException exception) {
            throw invalidListResponse("response is not valid JSON", exception);
        }

        JsonNode items = response == null ? null : response.get("items");
        if (items == null || !items.isArray()) {
            throw invalidListResponse("\"items\" must be an array");
        }

        List<ListedVector> vectors = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            JsonNode id = item == null || !item.isObject() ? null : item.get("id");
            if (id == null || !id.isTextual()) {
                throw invalidListResponse("each \"items\" entry must contain a string \"id\"");
            }

            try {
                JsonNode metadataNode = item.get("metadata");
                vectors.add(new ListedVector(ensureNotBlank(id.asText(), "id"), toMetadata(metadataNode)));
            } catch (IllegalArgumentException exception) {
                throw invalidListResponse(exception.getMessage(), exception);
            }
        }
        return List.copyOf(vectors);
    }

    /** Extracts vector IDs from a {@code DBMS_VECTOR_DATABASE.LIST_VECTORS} response. */
    public static List<String> idsFromListResponse(String responseJson) {
        return vectorsFromListResponse(responseJson).stream()
                .map(ListedVector::id)
                .toList();
    }

    private static Metadata toMetadata(JsonNode metadataNode) {
        if (metadataNode == null || metadataNode.isNull()) {
            return new Metadata();
        }
        if (!metadataNode.isObject()) {
            throw new IllegalArgumentException("each \"items\" entry must contain an object or null \"metadata\"");
        }

        Map<String, Object> metadata = OBJECT_MAPPER.convertValue(metadataNode, METADATA_TYPE);
        metadata.remove(TEXT_METADATA_KEY);
        return new Metadata(metadata);
    }

    private static IllegalStateException invalidListResponse(String message) {
        return new IllegalStateException("Invalid DBMS_VECTOR_DATABASE.LIST_VECTORS response: " + message);
    }

    private static IllegalStateException invalidListResponse(String message, Exception cause) {
        return new IllegalStateException("Invalid DBMS_VECTOR_DATABASE.LIST_VECTORS response: " + message, cause);
    }

    private static void ensureSameSize(List<?> first, String firstName, List<?> second, String secondName) {
        if (first.size() != second.size()) {
            throw new IllegalArgumentException(firstName + ".size() " + first.size() + " is not equal to " + secondName
                    + ".size() " + second.size());
        }
    }

    private static <T> T ensureElementNotNull(List<T> values, int index, String name) {
        T value = values.get(index);
        if (value == null) {
            throw new IllegalArgumentException("null entry at index " + index + " in " + name);
        }
        return value;
    }

    public record ListedVector(String id, Metadata metadata) {}
}
