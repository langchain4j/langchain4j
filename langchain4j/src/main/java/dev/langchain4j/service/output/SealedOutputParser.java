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
import dev.langchain4j.json.Polymorphic;
import dev.langchain4j.json.PolymorphicValue;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Internal
class SealedOutputParser<T> implements OutputParser<T> {

    private final Class<T> type;
    private final String discriminator;
    private final Map<String, Class<? extends T>> discriminatorValuesToTypes;

    SealedOutputParser(Class<T> type) {
        this.type = ensureNotNull(type, "type");
        if (!type.isSealed()) {
            throw illegalConfiguration("%s must be sealed to use polymorphic parsing", type.getName());
        }

        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        this.discriminator = polymorphic == null ? "type" : polymorphic.discriminator();

        Class<?>[] permittedSubclasses = type.getPermittedSubclasses();
        if (permittedSubclasses == null || permittedSubclasses.length == 0) {
            throw illegalConfiguration("No permitted subclasses found for %s", type.getName());
        }

        Map<String, Class<? extends T>> mapping = new LinkedHashMap<>();
        for (Class<?> permittedSubclass : permittedSubclasses) {
            @SuppressWarnings("unchecked")
            Class<? extends T> subType = (Class<? extends T>) permittedSubclass;
            PolymorphicValue polymorphicValue = subType.getAnnotation(PolymorphicValue.class);
            if (polymorphicValue == null) {
                throw illegalConfiguration(
                        "Permitted subclass %s of %s must be annotated with @PolymorphicValue",
                        subType.getName(), type.getName());
            }
            String discriminatorValue = polymorphicValue.value();
            if (mapping.containsKey(discriminatorValue)) {
                throw illegalConfiguration(
                        "Duplicate discriminator value '%s' detected for %s", discriminatorValue, type.getName());
            }
            mapping.put(discriminatorValue, subType);
        }
        this.discriminatorValuesToTypes = Map.copyOf(mapping);
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
            return Json.fromJson(parsedJson.json(), targetType);
        } catch (Exception e) {
            throw outputParsingException(text, targetType.getTypeName(), e);
        }
    }

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

    private static String structure(Class<?> subType) {
        String instructions = new PojoOutputParser<>(subType).formatInstructions();
        int index = instructions.indexOf(":");
        if (index < 0 || index == instructions.length() - 1) {
            return instructions.trim();
        }
        return instructions.substring(index + 1).trim();
    }
}
