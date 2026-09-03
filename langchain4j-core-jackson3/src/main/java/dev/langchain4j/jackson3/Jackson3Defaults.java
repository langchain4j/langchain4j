package dev.langchain4j.jackson3;

import dev.langchain4j.Internal;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;
/**
 * Jackson 3 changed a number of defaults. Every codec in this module restores the Jackson 2
 * values, so that swapping the JSON library does not also change behaviour. Adopting any of the
 * new defaults should be a deliberate, separately tested decision.
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
                // Jackson 3 disables these; without the first, final collection fields are
                // silently left empty on deserialization
                .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
                .enable(MapperFeature.USE_GETTERS_AS_SETTERS)
                // Jackson 2 reads "" as null for an enum rather than failing, and providers do
                // send it - an OpenAI-compatible server returning "type": "" for a tool call is
                // what found this. Scoped to enums on purpose: Jackson 2 fails on "" for a POJO,
                // a Map or a List, so coercing those too would make this codec more lenient than
                // the one it stands in for.
                .withCoercionConfig(
                        LogicalType.Enum,
                        config -> config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull));
    }
}
