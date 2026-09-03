package dev.langchain4j.jackson3;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 changed a number of defaults. Every codec in this module restores the Jackson 2
 * values, so that swapping the JSON library does not also change behaviour. Adopting any of the
 * new defaults should be a deliberate, separately tested decision.
 */
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
                // Jackson 2 reads "" as null rather than failing, and providers do send it -
                // an OpenAI-compatible server returning "type": "" for a tool call is what found
                // this. Jackson 3 refuses unless coercion is configured.
                .withCoercionConfigDefaults(
                        config -> config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull));
    }
}
