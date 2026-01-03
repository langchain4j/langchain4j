package dev.langchain4j.service.output;

import static dev.langchain4j.internal.JsonParsingUtils.extractAndParseJson;
import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonObjectOrReferenceSchemaFrom;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.output.ParsingUtils.outputParsingException;
import static java.lang.String.join;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonParsingUtils;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@Internal
class PolymorphicOutputParser<T> implements OutputParser<T> {

    private final Class<T> type;
    private final String discriminator;
    private final Map<String, Class<? extends T>> discriminatorValuesToTypes;

    PolymorphicOutputParser(Class<T> type) {
        this.type = ensureNotNull(type, "type");

        JsonTypeInfo jsonTypeInfo = type.getAnnotation(JsonTypeInfo.class);
        if (jsonTypeInfo == null) {
            throw illegalConfiguration("%s must be annotated with @JsonTypeInfo", type.getName());
        }

        String property = jsonTypeInfo.property();
        this.discriminator = property == null || property.isEmpty() ? "type" : property;

        Class<?>[] subtypes = resolveSubtypes(type);
        this.discriminatorValuesToTypes = mappingFromJacksonAnnotations(type, subtypes);
    }

    @Override
    public T parse(String text) {
        JsonParsingUtils.ParsedJson<Map> parsedJson;
        try {
            parsedJson = extractAndParseJson(text, Map.class);
        } catch (Exception e) {
            throw outputParsingException(text, type.getTypeName(), e);
        }

        Object discriminatorValue = parsedJson.value().get(discriminator);
        if (discriminatorValue == null) {
            throw new OutputParsingException(
                    "Missing discriminator '" + discriminator + "' for " + type.getName(), null);
        }

        Class<? extends T> targetType = discriminatorValuesToTypes.get(discriminatorValue.toString());
        if (targetType == null) {
            throw new OutputParsingException("Unknown discriminator value: " + discriminatorValue, null);
        }

        try {
            return targetType.cast(deserializeBypassingTypeId(parsedJson.value(), discriminator, targetType));
        } catch (Exception e) {
            throw outputParsingException(text, targetType.getTypeName(), e);
        }
    }

    /**
     * We have already resolved the subtype using the discriminator. To avoid Jackson re-validating
     * type ids (which would require the discriminator field to be present and match), deserialize
     * with a TypeDeserializer that ignores external type ids.
     */
    private static <R> R deserializeBypassingTypeId(
            Map<String, Object> originalPayload, String discriminator, Class<R> targetType) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(targetType, NoTypeInfoMixin.class);
        return mapper.convertValue(originalPayload, targetType);
    }

    @JsonTypeInfo(use = Id.NONE, include = As.PROPERTY)
    private interface NoTypeInfoMixin {}

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonAnyOfSchema anyOfSchema = JsonAnyOfSchema.builder()
                .anyOf(discriminatorValuesToTypes.values().stream()
                        .map(type -> jsonObjectOrReferenceSchemaFrom(type, null, false, new LinkedHashMap<>(), true))
                        .toList())
                .build();

        return Optional.of(JsonSchema.builder()
                .name(type.getSimpleName())
                .rootElement(anyOfSchema)
                .build());
    }

    @Override
    public String formatInstructions() {
        StringBuilder instructions = new StringBuilder();
        instructions
                .append("\nYou must answer with JSON containing discriminator '")
                .append(discriminator)
                .append("' set to one of [")
                .append(join(", ", discriminatorValuesToTypes.keySet()))
                .append("].");

        discriminatorValuesToTypes.forEach((value, subType) -> instructions
                .append("\n- When ")
                .append(discriminator)
                .append("=")
                .append(value)
                .append(", follow: ")
                .append(structure(subType)));

        return instructions.toString();
    }

    private Map<String, Class<? extends T>> mappingFromJacksonAnnotations(Class<T> type, Class<?>[] subtypes) {

        Map<Class<?>, String> explicitNames = explicitNamesFromJsonSubTypes(type);

        Map<String, Class<? extends T>> mapping = new LinkedHashMap<>();
        for (Class<?> permittedSubclass : subtypes) {
            @SuppressWarnings("unchecked")
            Class<? extends T> subType = (Class<? extends T>) permittedSubclass;

            String discriminatorValue = explicitNames.get(subType);
            if (discriminatorValue == null || discriminatorValue.isEmpty()) {
                JsonTypeName jsonTypeName = subType.getAnnotation(JsonTypeName.class);
                if (jsonTypeName != null && !jsonTypeName.value().isEmpty()) {
                    discriminatorValue = jsonTypeName.value();
                }
            }
            if (discriminatorValue == null || discriminatorValue.isEmpty()) {
                discriminatorValue = subType.getSimpleName();
            }

            putMapping(type, mapping, discriminatorValue, subType);
        }
        return Map.copyOf(mapping);
    }

    private Map<Class<?>, String> explicitNamesFromJsonSubTypes(Class<T> type) {
        Map<Class<?>, String> explicitNames = new HashMap<>();
        JsonSubTypes jsonSubTypes = type.getAnnotation(JsonSubTypes.class);
        if (jsonSubTypes != null) {
            for (JsonSubTypes.Type jsonSubType : jsonSubTypes.value()) {
                Class<?> value = jsonSubType.value();
                String name = jsonSubType.name();
                if (!name.isEmpty()) {
                    explicitNames.put(value, name);
                }
            }
        }
        return explicitNames;
    }

    private Class<?>[] resolveSubtypes(Class<T> type) {
        JsonSubTypes jsonSubTypes = type.getAnnotation(JsonSubTypes.class);
        if (jsonSubTypes != null && jsonSubTypes.value().length > 0) {
            return java.util.Arrays.stream(jsonSubTypes.value())
                    .map(JsonSubTypes.Type::value)
                    .toArray(Class<?>[]::new);
        }

        Class<?>[] permitted = type.getPermittedSubclasses();
        if (permitted != null && permitted.length > 0) {
            return permitted;
        }

        throw illegalConfiguration(
                "No subtypes found for %s. Please declare @JsonSubTypes or make the type sealed with permitted subclasses.",
                type.getName());
    }

    private static <T> void putMapping(
            Class<?> parentType,
            Map<String, Class<? extends T>> mapping,
            String discriminatorValue,
            Class<? extends T> subType) {
        if (mapping.containsKey(discriminatorValue)) {
            throw illegalConfiguration(
                    "Duplicate discriminator value '%s' detected for %s", discriminatorValue, parentType.getName());
        }
        mapping.put(discriminatorValue, subType);
    }

    private static String structure(Class<?> subType) {
        String instructions = new PojoOutputParser<>(subType).formatInstructions();
        int index = instructions.indexOf(":");
        if (index < 0 || index == instructions.length() - 1) {
            return instructions.trim();
        }
        return instructions.substring(index + 1).trim();
    }
}
