package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Maps LangChain4j embeddings and text segments to the {@code vectors} JSON accepted by
 * {@code DBMS_VECTOR_DATABASE.UPSERT_VECTORS}.
 */
public final class VecDbVectorJsonMapper {

    static final String TEXT_METADATA_KEY = "text";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VecDbVectorJsonMapper() {}

    /**
     * Maps embeddings without text or metadata to VecDB vector records.
     */
    public static String toJson(List<String> ids, List<Embedding> embeddings) {
        return recordsToJson(toRecords(ids, embeddings, null));
    }

    /**
     * Maps embeddings, text, and metadata to VecDB vector records.
     */
    public static String toJson(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        ensureNotNull(segments, "segments");
        return recordsToJson(toRecords(ids, embeddings, segments));
    }

    /** Returns JSON text for batches whose complete OSON encodings fit the database limit. */
    public static List<String> toJsonBatches(List<String> ids, List<Embedding> embeddings) {
        return toJsonBatches(ids, embeddings, null, VecDbJsonMapper.MAX_OSON_BYTES);
    }

    /** Returns JSON text for complete-record batches sized by their OSON encodings, not their text lengths. */
    public static List<String> toJsonBatches(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        ensureNotNull(segments, "segments");
        return toJsonBatches(ids, embeddings, segments, VecDbJsonMapper.MAX_OSON_BYTES);
    }

    /** Prepares ordered vector-only OSON requests that can be bound directly as {@code OracleType.JSON}. */
    public static List<byte[]> toOsonBatches(List<String> ids, List<Embedding> embeddings) {
        return toBatches(toRecords(ids, embeddings, null), VecDbJsonMapper.MAX_OSON_BYTES, (records, bytes) -> bytes);
    }

    /** Prepares all complete-record OSON requests before any database write, retaining the measured bytes. */
    public static List<byte[]> toOsonBatches(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        ensureNotNull(segments, "segments");
        return toBatches(
                toRecords(ids, embeddings, segments), VecDbJsonMapper.MAX_OSON_BYTES, (records, bytes) -> bytes);
    }

    static List<String> toJsonBatches(
            List<String> ids, List<Embedding> embeddings, List<TextSegment> segments, int maxBatchBytes) {
        return toBatches(
                toRecords(ids, embeddings, segments), maxBatchBytes, (records, bytes) -> recordsToJson(records));
    }

    private static List<JsonNode> toRecords(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        ensureNotNull(ids, "ids");
        ensureNotNull(embeddings, "embeddings");
        ensureSameSize(ids, "ids", embeddings, "embeddings");
        if (segments != null) {
            ensureSameSize(ids, "ids", segments, "segments");
        }
        List<JsonNode> records = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            ObjectNode record = toJsonObject(
                    ensureElementNotNull(ids, i, "ids"), ensureElementNotNull(embeddings, i, "embeddings"));
            if (segments != null) {
                record.set("metadata", toMetadataJson(ensureElementNotNull(segments, i, "segments")));
            }

            records.add(record);
        }
        return records;
    }

    private static String recordsToJson(List<JsonNode> records) {
        return OBJECT_MAPPER.createArrayNode().addAll(records).toString();
    }

    private static <T> List<T> toBatches(
            List<JsonNode> records, int maxBatchBytes, BiFunction<List<JsonNode>, byte[], T> batchMapper) {
        ensureGreaterThanZero(maxBatchBytes, "maxBatchBytes");
        List<T> batches = new ArrayList<>();
        if (!records.isEmpty()) {
            try {
                addBatches(records, 0, maxBatchBytes, batchMapper, batches);
            } catch (SQLException exception) {
                throw new IllegalArgumentException("Unable to encode VecDB upsert records as OSON", exception);
            }
        }
        return List.copyOf(batches);
    }

    private static <T> void addBatches(
            List<JsonNode> records,
            int firstIndex,
            int maxBatchBytes,
            BiFunction<List<JsonNode>, byte[], T> batchMapper,
            List<T> batches)
            throws SQLException {
        VecDbJsonMapper.EncodedArray encoded = VecDbJsonMapper.encodeArray(records, maxBatchBytes);
        if (encoded.bytes() != null) {
            batches.add(batchMapper.apply(records, encoded.bytes()));
            return;
        }
        if (records.size() == 1) {
            throw new IllegalArgumentException("VecDB vector at index " + firstIndex + " requires " + encoded.size()
                    + " OSON bytes, exceeding the upsert batch limit of " + maxBatchBytes
                    + " bytes. Reduce the text segment size or metadata for this record.");
        }

        // OSON headers, offsets, and shared field names make per-record size sums unreliable.
        int middle = records.size() / 2;
        addBatches(records.subList(0, middle), firstIndex, maxBatchBytes, batchMapper, batches);
        addBatches(records.subList(middle, records.size()), firstIndex + middle, maxBatchBytes, batchMapper, batches);
    }

    private static ObjectNode toJsonObject(String id, Embedding embedding) {
        ObjectNode vector = OBJECT_MAPPER.createObjectNode();
        vector.put("id", ensureNotBlank(id, "id"));

        ArrayNode denseVector = vector.putArray("dense_vector");
        for (float value : embedding.vector()) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding vector values must be finite");
            }
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

    static Metadata toMetadata(JsonNode metadataNode) {
        if (metadataNode == null || metadataNode.isNull()) {
            return new Metadata();
        }
        if (!metadataNode.isObject()) {
            throw new IllegalArgumentException("each \"items\" entry must contain an object or null \"metadata\"");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> property : metadataNode.properties()) {
            if (!TEXT_METADATA_KEY.equals(property.getKey())) {
                metadata.put(property.getKey(), toMetadataValue(property.getKey(), property.getValue()));
            }
        }
        return new Metadata(metadata);
    }

    private static Object toMetadataValue(String key, JsonNode value) {
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isIntegralNumber()) {
            if (value.canConvertToInt()) {
                return value.intValue();
            }
            if (value.canConvertToLong()) {
                return value.longValue();
            }
            double doubleValue = value.doubleValue();
            if (Double.isFinite(doubleValue)) {
                return doubleValue;
            }
        }
        if (value.isFloatingPointNumber()) {
            double doubleValue = value.doubleValue();
            if (Double.isFinite(doubleValue)) {
                return doubleValue;
            }
        }
        throw new IllegalArgumentException(
                "metadata property \"" + key + "\" cannot be converted to a LangChain4j metadata value");
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
