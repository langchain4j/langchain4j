package dev.langchain4j.internal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

class JacksonJsonCodecFirst implements Json.JsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            // TODO Jackson 3.X? JsonMapper?
//        .registerModule(new JavaTimeModule()) // TODO enable only if found in classpath?
//        .registerModule(new Jdk8Module()) // TODO enable only if found in classpath?
//        .registerModule(new ParameterNamesModule()) // TODO enable only if found in classpath?
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .enable(INDENT_OUTPUT)
            .disable(FAIL_ON_UNKNOWN_PROPERTIES) // TODO good idea?
            .disable(FAIL_ON_NULL_FOR_PRIMITIVES); // TODO good idea?

    @Override
    public String toJson(Object o) {
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.constructType(type));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream toInputStream(Object o, Class<?> type) throws IOException {
        return new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(o));
    }
}
