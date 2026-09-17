package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.oracle.vecdb.mapper.VecDbVectorJsonMapper.TEXT_METADATA_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.util.ArrayList;
import java.util.List;

/** Maps a {@code DBMS_VECTOR_DATABASE.SEARCH} response to LangChain4j search results. */
public final class VecDbSearchResultMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VecDbSearchResultMapper() {}

    public static EmbeddingSearchResult<TextSegment> map(
            String responseJson, double minScore, VecDbDistanceMetric distanceMetric) {
        ensureNotBlank(responseJson, "responseJson");
        ensureBetween(minScore, 0.0, 1.0, "minScore");
        ensureNotNull(distanceMetric, "distanceMetric");

        JsonNode results = requiredField(readTree(responseJson), "results");
        if (!results.isArray()) {
            throw invalidResponse("\"results\" must be an array");
        }

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>(results.size());
        for (JsonNode result : results) {
            if (!result.isObject()) {
                throw invalidResponse("each \"results\" entry must be an object");
            }

            double score = distanceToScore(requiredFiniteNumber(result, "distance"), distanceMetric);
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

    private static double distanceToScore(double distance, VecDbDistanceMetric distanceMetric) {
        return switch (distanceMetric) {
            case COSINE -> {
                if (distance < 0.0 || distance > 2.0) {
                    throw invalidResponse("cosine \"distance\" must be between 0 and 2");
                }
                yield 1.0 - distance / 2.0;
            }
            case EUCLIDEAN, MANHATTAN -> 1.0 / (1.0 + requireNonNegativeDistance(distance, distanceMetric));
            case L2_SQUARED, EUCLIDEAN_SQUARED ->
                1.0 / (1.0 + Math.sqrt(requireNonNegativeDistance(distance, distanceMetric)));
            case DOT -> clampScore(-distance);
        };
    }

    private static double requireNonNegativeDistance(double distance, VecDbDistanceMetric distanceMetric) {
        if (distance < 0.0) {
            throw invalidResponse(distanceMetric + " \"distance\" must not be negative");
        }
        return distance;
    }

    private static double clampScore(double score) {
        return Math.max(0.0, Math.min(1.0, score));
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
            return TextSegment.from(text.asText(), VecDbVectorJsonMapper.toMetadata(metadataObject));
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
