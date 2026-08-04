package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.store.embedding.oracle.vecdb.OracleVecDbEmbeddingStore;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.util.Locale;
import java.util.Map;

public final class VecDbEmbeddingTableJsonMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ANNOTATIONS_TYPE = new TypeReference<>() {};

    private VecDbEmbeddingTableJsonMapper() {}

    public static String tableParametersToJson() {
        ObjectNode tableParameters = OBJECT_MAPPER.createObjectNode();
        tableParameters.put("auto_generate_id", false);
        return tableParameters.toString();
    }

    public static String annotationsToJson(Map<String, Object> annotations) {
        if (annotations.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(annotations);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize VecDB table annotations", exception);
        }
    }

    public static OracleVecDbEmbeddingStore.VectorTableDescription descriptionFromJson(String responseJson) {
        JsonNode response = readDescription(responseJson);

        String tableName = requiredText(response, "table_name");
        String comment = optionalText(response, "comment");
        if (comment == null) {
            comment = optionalText(response, "description");
        }

        JsonNode annotationsNode = field(response, "annotations");
        Map<String, Object> annotations = Map.of();
        if (annotationsNode != null && !annotationsNode.isNull()) {
            if (!annotationsNode.isObject()) {
                throw invalidResponse("'annotations' must be a JSON object", null);
            }
            annotations = OBJECT_MAPPER.convertValue(annotationsNode, ANNOTATIONS_TYPE);
        }

        return new OracleVecDbEmbeddingStore.VectorTableDescription(tableName, comment, annotations);
    }

    public static IndexStatus indexStatusFromJson(String responseJson, VecDbApiVersion apiVersion) {
        JsonNode response = readDescription(responseJson);
        return switch (ensureNotNull(apiVersion, "apiVersion")) {
            case V23_26_1 -> legacyIndexStatus(response);
            case V23_26_3 -> newIndexStatus(response);
        };
    }

    public static VecDbDistanceMetric effectiveDistanceMetricFromJson(String responseJson, VecDbApiVersion apiVersion) {
        JsonNode response = readDescription(responseJson);
        apiVersion = ensureNotNull(apiVersion, "apiVersion");
        IndexStatus indexStatus =
                switch (apiVersion) {
                    case V23_26_1 -> legacyIndexStatus(response);
                    case V23_26_3 -> newIndexStatus(response);
                };
        if (!indexStatus.vectorIndexExists()) {
            return VecDbDistanceMetric.COSINE;
        }

        JsonNode indexParameters = field(response, "index_params");
        JsonNode metric =
                switch (apiVersion) {
                    case V23_26_1 -> field(indexParameters, "distance_metric");
                    case V23_26_3 -> field(field(indexParameters, "vector_index_params"), "distance_metric");
                };
        if (metric == null || metric.isNull()) {
            return VecDbDistanceMetric.COSINE;
        }
        if (!metric.isTextual() || metric.asText().isBlank()) {
            throw invalidResponse("'distance_metric' must be a non-blank string", null);
        }

        try {
            return VecDbDistanceMetric.valueOf(metric.asText().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new UnsupportedFeatureException(
                    "Cannot convert VecDB distances produced by metric " + metric.asText() + " to LangChain4j scores");
        }
    }

    private static IndexStatus legacyIndexStatus(JsonNode response) {
        boolean vectorIndexExists = hasNonBlankText(response, "dense_idx_name");
        return new IndexStatus(vectorIndexExists, false);
    }

    private static IndexStatus newIndexStatus(JsonNode response) {
        boolean vectorIndexExists = false;
        boolean metadataIndexExists = false;
        JsonNode indexes = field(response, "indexes");
        if (indexes != null && indexes.isArray()) {
            for (JsonNode index : indexes) {
                if (isVectorIndex(index)) {
                    vectorIndexExists = true;
                }
                if (isMetadataIndex(index)) {
                    metadataIndexExists = true;
                }
            }
        }

        JsonNode indexParameters = field(response, "index_params");
        JsonNode vectorIndexParameters = field(indexParameters, "vector_index_params");
        if (!vectorIndexExists) {
            vectorIndexExists = isAutoIndexEnabled(vectorIndexParameters);
        }

        JsonNode metadataIndexParameters = field(indexParameters, "metadata_index_params");
        if (!metadataIndexExists) {
            metadataIndexExists = isAutoIndexEnabled(metadataIndexParameters)
                    || hasConfiguredPaths(metadataIndexParameters, "include_paths");
        }

        return new IndexStatus(vectorIndexExists, metadataIndexExists);
    }

    private static JsonNode readDescription(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw invalidResponse("response is empty", null);
        }

        try {
            JsonNode response = OBJECT_MAPPER.readTree(responseJson);
            if (response == null || !response.isObject()) {
                throw invalidResponse("response must be a JSON object", null);
            }
            return response;
        } catch (JsonProcessingException exception) {
            throw invalidResponse("response is not valid JSON", exception);
        }
    }

    private static String requiredText(JsonNode object, String fieldName) {
        String value = optionalText(object, fieldName);
        if (value == null || value.isBlank()) {
            throw invalidResponse("'" + fieldName + "' is missing or blank", null);
        }
        return value;
    }

    private static String optionalText(JsonNode object, String fieldName) {
        JsonNode value = field(object, fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidResponse("'" + fieldName + "' must be a string", null);
        }
        return value.textValue();
    }

    private static JsonNode field(JsonNode object, String fieldName) {
        if (object == null || !object.isObject()) {
            return null;
        }

        JsonNode exactMatch = object.get(fieldName);
        if (exactMatch != null) {
            return exactMatch;
        }

        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (field.getKey().equalsIgnoreCase(fieldName)) {
                return field.getValue();
            }
        }
        return null;
    }

    private static boolean hasNonBlankText(JsonNode object, String fieldName) {
        JsonNode value = field(object, fieldName);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    private static boolean isVectorIndex(JsonNode index) {
        if (index.isTextual()) {
            return index.asText().toUpperCase(Locale.ROOT).startsWith("VECIDX");
        }
        if (!index.isObject()) {
            return false;
        }

        JsonNode type = field(index, "index_type");
        if (type == null) {
            type = field(index, "type");
        }
        if (type != null && type.asText().toUpperCase(Locale.ROOT).contains("VECTOR")) {
            return true;
        }

        JsonNode name = field(index, "index_name");
        if (name == null) {
            name = field(index, "name");
        }
        return name != null && name.asText().toUpperCase(Locale.ROOT).startsWith("VECIDX");
    }

    private static boolean isMetadataIndex(JsonNode index) {
        if (index.isTextual()) {
            String value = index.asText().toUpperCase(Locale.ROOT);
            return value.startsWith("MVI") || value.contains("METADATA");
        }
        if (!index.isObject()) {
            return false;
        }

        JsonNode type = field(index, "index_type");
        if (type == null) {
            type = field(index, "type");
        }
        if (type != null && type.asText().toUpperCase(Locale.ROOT).contains("METADATA")) {
            return true;
        }

        JsonNode name = field(index, "index_name");
        if (name == null) {
            name = field(index, "name");
        }
        if (name == null) {
            return false;
        }
        String value = name.asText().toUpperCase(Locale.ROOT);
        return value.startsWith("MVI") || value.contains("METADATA");
    }

    private static boolean isAutoIndexEnabled(JsonNode indexParameters) {
        JsonNode autoIndex = field(indexParameters, "auto_index");
        return autoIndex != null && autoIndex.asBoolean(false);
    }

    private static boolean hasConfiguredPaths(JsonNode indexParameters, String fieldName) {
        JsonNode paths = field(indexParameters, fieldName);
        return paths != null && paths.isArray() && !paths.isEmpty();
    }

    private static IllegalStateException invalidResponse(String message, Exception cause) {
        return new IllegalStateException(
                "Invalid DBMS_VECTOR_DATABASE.DESCRIBE_VECTOR_TABLE response: " + message, cause);
    }

    public record IndexStatus(boolean vectorIndexExists, boolean metadataIndexExists) {}
}
