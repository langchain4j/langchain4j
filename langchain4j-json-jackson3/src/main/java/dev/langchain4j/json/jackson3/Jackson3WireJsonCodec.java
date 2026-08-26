package dev.langchain4j.json.jackson3;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJsonSpec;
import java.lang.reflect.Type;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 codec for provider wire DTOs.
 *
 * <p>The defaults Jackson 3 changed are pinned to their Jackson 2 values, so switching the codec
 * does not also change what goes on the wire.
 */
public class Jackson3WireJsonCodec implements Json.JsonCodec {

    private final ObjectMapper objectMapper;

    public Jackson3WireJsonCodec(WireJsonSpec spec) {
        JsonMapper.Builder builder = Jackson3Defaults.pinJackson2Defaults(JsonMapper.builder())
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                // a provider adding a field must never break deserialization
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // --- Jackson 3 changed these defaults; keep 2.x behaviour ---
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        if (spec.prettyPrint()) {
            builder.enable(SerializationFeature.INDENT_OUTPUT);
        }
        if (spec.propertyNaming() == WireJsonSpec.PropertyNaming.SNAKE_CASE) {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }
        builder.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(toJacksonInclude(spec.inclusion())));
        this.objectMapper = builder.build();
    }

    private static JsonInclude.Include toJacksonInclude(WireJsonSpec.Inclusion inclusion) {
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
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return objectMapper.readValue(json, objectMapper.constructType(type));
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
