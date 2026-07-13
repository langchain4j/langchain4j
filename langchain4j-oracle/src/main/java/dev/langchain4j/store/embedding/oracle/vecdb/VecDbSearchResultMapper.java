package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbVectorJsonMapper.TEXT_METADATA_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Maps a {@code DBMS_VECTOR_DATABASE.SEARCH} response to LangChain4j search results. */
final class VecDbSearchResultMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

    private VecDbSearchResultMapper() {}

    static EmbeddingSearchResult<TextSegment> map(
            String responseJson, double minScore, VecDbDistanceMetric distanceMetric) {
        ensureNotBlank(responseJson, "responseJson");
        ensureBetween(minScore, 0.0, 1.0, "minScore");
        ensureNotNull(distanceMetric, "distanceMetric");
        requireCosineMetric(distanceMetric);

        JsonNode results = requiredField(readTree(responseJson), "results");
        if (!results.isArray()) {
            throw invalidResponse("\"results\" must be an array");
        }

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>(results.size());
        for (JsonNode result : results) {
            if (!result.isObject()) {
                throw invalidResponse("each \"results\" entry must be an object");
            }

            double score = cosineScore(requiredFiniteNumber(result, "distance"));
            if (score < minScore) {
                continue;
            }

            matches.add(new EmbeddingMatch<>(
                    score,
                    requiredText(result, "id"),
                    toEmbedding(result.get("vector")),
                    toTextSegment(result.get("metadata"))));
        }

        return new EmbeddingSearchResult<>(matches);
    }

    private static void requireCosineMetric(VecDbDistanceMetric distanceMetric) {
        if (distanceMetric != VecDbDistanceMetric.COSINE) {
            throw new UnsupportedFeatureException(
                    "VecDB search result scoring currently supports only COSINE distance, but was " + distanceMetric);
        }
    }

    private static double cosineScore(double distance) {
        if (distance < 0.0 || distance > 2.0) {
            throw invalidResponse("cosine \"distance\" must be between 0 and 2");
        }
        // Oracle cosine distance is in [0, 2]; LangChain4j relevance score reverses it into [1, 0].
        return 1.0 - distance / 2.0;
    }

    private static Embedding toEmbedding(JsonNode vector) {
        if (vector == null || vector.isNull()) {
            return null;
        }
        if (!vector.isArray()) {
            throw invalidResponse("\"vector\" must be an array");
        }

        float[] values = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            JsonNode value = vector.get(i);
            if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                throw invalidResponse("\"vector\" entries must be finite numbers");
            }
            values[i] = value.floatValue();
            if (!Float.isFinite(values[i])) {
                throw invalidResponse("\"vector\" entry is outside the FLOAT32 range");
            }
        }
        return new Embedding(values);
    }

    private static TextSegment toTextSegment(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return null;
        }
        if (!metadata.isObject()) {
            throw invalidResponse("\"metadata\" must be an object");
        }

        ObjectNode metadataObject = ((ObjectNode) metadata).deepCopy();
        JsonNode text = metadataObject.remove(TEXT_METADATA_KEY);
        if (text == null || text.isNull()) {
            return null;
        }
        if (!text.isTextual()) {
            throw invalidResponse("metadata property \"" + TEXT_METADATA_KEY + "\" must be a string");
        }

        try {
            Map<String, Object> values = OBJECT_MAPPER.convertValue(metadataObject, METADATA_TYPE);
            return TextSegment.from(text.asText(), new Metadata(values));
        } catch (IllegalArgumentException exception) {
            throw invalidResponse("metadata contains an invalid LangChain4j TextSegment value", exception);
        }
    }

    private static String requiredText(JsonNode object, String fieldName) {
        JsonNode value = requiredField(object, fieldName);
        if (!value.isTextual()) {
            throw invalidResponse("\"" + fieldName + "\" must be a string");
        }
        try {
            return ensureNotBlank(value.asText(), fieldName);
        } catch (IllegalArgumentException exception) {
            throw invalidResponse("\"" + fieldName + "\" must not be blank", exception);
        }
    }

    private static double requiredFiniteNumber(JsonNode object, String fieldName) {
        JsonNode value = requiredField(object, fieldName);
        double number = value.asDouble(Double.NaN);
        if (!value.isNumber() || !Double.isFinite(number)) {
            throw invalidResponse("\"" + fieldName + "\" must be a finite number");
        }
        return number;
    }

    private static JsonNode requiredField(JsonNode object, String fieldName) {
        if (object == null || !object.isObject()) {
            throw invalidResponse("response root must be an object");
        }
        JsonNode value = object.get(fieldName);
        if (value == null || value.isNull()) {
            throw invalidResponse("missing required property \"" + fieldName + "\"");
        }
        return value;
    }

    private static JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw invalidResponse("response is not valid JSON", exception);
        }
    }

    private static IllegalStateException invalidResponse(String message) {
        return new IllegalStateException("Invalid DBMS_VECTOR_DATABASE.SEARCH response: " + message);
    }

    private static IllegalStateException invalidResponse(String message, Exception cause) {
        return new IllegalStateException("Invalid DBMS_VECTOR_DATABASE.SEARCH response: " + message, cause);
    }
}
