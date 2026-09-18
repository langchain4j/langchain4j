package dev.langchain4j.internal;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.Internal;
import java.lang.reflect.Type;

/**
 * Default {@link Json.JsonCodec} for provider wire DTOs, backed by Jackson 2.
 */
@Internal
class JacksonProviderJsonCodec implements Json.JsonCodec {

    private final ObjectMapper objectMapper;

    JacksonProviderJsonCodec(ProviderJsonSpec spec) {
        JsonMapper.Builder builder = JsonMapper.builder()
                .disable(FAIL_ON_IGNORED_PROPERTIES)
                // a provider adding a field must never break deserialization
                .disable(FAIL_ON_UNKNOWN_PROPERTIES);
        if (spec.prettyPrint()) {
            builder.enable(INDENT_OUTPUT);
        }
        if (spec.propertyNaming() == ProviderJsonSpec.PropertyNaming.SNAKE_CASE) {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }
        builder.serializationInclusion(toJacksonInclude(spec.inclusion()));
        this.objectMapper = builder.build();
    }

    private static JsonInclude.Include toJacksonInclude(ProviderJsonSpec.Inclusion inclusion) {
        return switch (inclusion) {
            case NON_NULL -> JsonInclude.Include.NON_NULL;
            case NON_EMPTY -> JsonInclude.Include.NON_EMPTY;
            case ALWAYS -> JsonInclude.Include.ALWAYS;
        };
    }

    @Override
    public String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return objectMapper.readValue(json, objectMapper.constructType(type));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
