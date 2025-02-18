package dev.langchain4j.model.openai.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

class Json {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(INDENT_OUTPUT)
            .disable(FAIL_ON_IGNORED_PROPERTIES);

    static String toJson(Object o) {
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
    }

    static <T> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
    }
}
