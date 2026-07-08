package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import java.util.Map;

/**
 * Maps LangChain4j embeddings and text segments to the {@code vectors} JSON accepted by
 * {@code DBMS_VECTOR_DATABASE.UPSERT_VECTORS}.
 */
final class VecDbVectorJsonMapper {

    static final String TEXT_METADATA_KEY = "text";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VecDbVectorJsonMapper() {}

    /**
     * Maps embeddings without text or metadata to VecDB vector records.
     */
    static String toJson(List<String> ids, List<Embedding> embeddings) {
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
    static String toJson(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
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
        vector.put("id", id);

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
}
