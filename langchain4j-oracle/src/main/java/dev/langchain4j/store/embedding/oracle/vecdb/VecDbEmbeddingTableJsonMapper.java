package dev.langchain4j.store.embedding.oracle.vecdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

final class VecDbEmbeddingTableJsonMapper {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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
            return OBJECT_MAPPER.writeValueAsString(
                    annotations);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize VecDB table annotations",
                    exception);
        }
    }

}
