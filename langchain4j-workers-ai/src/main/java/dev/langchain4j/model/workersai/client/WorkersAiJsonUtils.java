package dev.langchain4j.model.workersai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Internal JSON (de)serialization helper for the Workers AI client.
 *
 * <p>The mapper is intentionally a plain {@link ObjectMapper} with default configuration, mirroring
 * the behavior of the Jackson converter that was previously used by the Retrofit-based client.</p>
 */
class WorkersAiJsonUtils {

    private WorkersAiJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
