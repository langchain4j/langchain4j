package dev.langchain4j.json.jackson3;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;

import dev.langchain4j.internal.Json;
import java.lang.reflect.Type;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 implementation of {@link Json.JsonCodec}.
 *
 * <p>Configuration mirrors the Jackson 2 default codec, including the 2.x defaults that
 * Jackson 3 changed. Those are pinned explicitly so that swapping the codec does not also
 * change behaviour.
 */
public class Jackson3JsonCodec implements Json.JsonCodec {

    private final ObjectMapper objectMapper;

    static ObjectMapper createObjectMapper() {
        return Jackson3Defaults.pinJackson2Defaults(JsonMapper.builder())
                .changeDefaultVisibility(vc -> vc.withVisibility(FIELD, ANY))
                // same intent as the Jackson 2 codec
                .disable(SerializationFeature.INDENT_OUTPUT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .addModule(Jackson3LangChain4jModule.create())
                .annotationIntrospector(new tools.jackson.databind.introspect.AnnotationIntrospectorPair(
                        new Jackson3SealedTypeIntrospector(),
                        new tools.jackson.databind.introspect.JacksonAnnotationIntrospector()))
                .build();
    }

    public Jackson3JsonCodec() {
        this(createObjectMapper());
    }

    public Jackson3JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toJson(Object o) {
        return objectMapper.writeValueAsString(o);
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        return objectMapper.readValue(json, objectMapper.constructType(type));
    }
}
