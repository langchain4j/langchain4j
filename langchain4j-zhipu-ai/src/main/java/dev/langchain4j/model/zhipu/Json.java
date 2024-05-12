package dev.langchain4j.model.zhipu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

class Json {
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(INDENT_OUTPUT);

    @SneakyThrows
    static String toJson(Object o) {
        return OBJECT_MAPPER.writeValueAsString(o);
    }

    @SneakyThrows
    static <T> T fromJson(String json, Class<T> type) {
        return OBJECT_MAPPER.readValue(json, type);
    }

    @SneakyThrows
    static <T> T fromJson(String json, TypeReference<T> type) {
        return OBJECT_MAPPER.readValue(json, type);
    }
}