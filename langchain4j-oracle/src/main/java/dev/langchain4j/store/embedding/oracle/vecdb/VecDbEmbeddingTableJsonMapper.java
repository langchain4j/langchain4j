package dev.langchain4j.store.embedding.oracle.vecdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class VecDbEmbeddingTableJsonMapper {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private VecDbEmbeddingTableJsonMapper() {}

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
