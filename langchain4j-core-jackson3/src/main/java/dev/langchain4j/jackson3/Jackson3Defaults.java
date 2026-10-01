package dev.langchain4j.jackson3;

import dev.langchain4j.Internal;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;
/**
 * Jackson 3 changed a number of defaults. Every codec in this module restores the Jackson 2
 * values, so that swapping the JSON library does not also change behaviour. The few differences
 * that remain are deliberate, listed in the Jackson 3 guide and covered by tests; adopting any
 * other new default should be a deliberate, separately tested decision too.
 */
@Internal
public final class Jackson3Defaults {

    private Jackson3Defaults() {}

    public static JsonMapper.Builder pinJackson2Defaults(JsonMapper.Builder builder) {
        return builder
                // Jackson 3 enables these
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                // with parameter names, a one-argument constructor stops accepting a plain value
                // and a constructor is preferred over the no-argument one
                .disable(MapperFeature.DETECT_PARAMETER_NAMES)
                // Jackson 3 disables these; without the first, final collection fields are
                // silently left empty on deserialization
                .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
                .enable(MapperFeature.USE_GETTERS_AS_SETTERS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // Deliberately more lenient than Jackson 2, which fails on "" for an enum: providers
                // send it - an OpenAI-compatible server returning "type": "" for a tool call is what
                // found this - and an LLM may answer "" for an optional enum. Scoped to enums on
                // purpose, so that "" for a POJO, a Map or a List still fails as it does under Jackson 2.
                .withCoercionConfig(
                        LogicalType.Enum,
                        config -> config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull));
    }
}
