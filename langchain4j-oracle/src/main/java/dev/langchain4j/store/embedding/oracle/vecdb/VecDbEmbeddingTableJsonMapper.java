package dev.langchain4j.store.embedding.oracle.vecdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

final class VecDbEmbeddingTableJsonMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ANNOTATIONS_TYPE = new TypeReference<>() {};

    private VecDbEmbeddingTableJsonMapper() {}

    static String tableParametersToJson() {
        ObjectNode tableParameters = OBJECT_MAPPER.createObjectNode();
        tableParameters.put("auto_generate_id", false);
        return tableParameters.toString();
    }

    static String annotationsToJson(Map<String, Object> annotations) {
        if (annotations.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(annotations);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize VecDB table annotations", exception);
        }
    }

    static OracleVecDbEmbeddingStore.VectorTableDescription descriptionFromJson(String responseJson) {
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

    private static IllegalStateException invalidResponse(String message, Exception cause) {
        return new IllegalStateException(
                "Invalid DBMS_VECTOR_DATABASE.DESCRIBE_VECTOR_TABLE response: " + message, cause);
    }
}
