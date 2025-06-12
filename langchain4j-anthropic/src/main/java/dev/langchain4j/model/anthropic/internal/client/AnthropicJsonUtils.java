package dev.langchain4j.model.anthropic.internal.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;

import java.io.IOException;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

@Internal
class AnthropicJsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
