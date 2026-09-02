package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class McpSchemaToType {

    private McpSchemaToType() {}

    static List<AgentArgument> arguments(JsonObjectSchema parameters, String[] inputKeys) {
        if (inputKeys == null || inputKeys.length == 0) {
            return List.of();
        }

        Map<String, JsonSchemaElement> properties =
                parameters == null || parameters.properties() == null ? Map.of() : parameters.properties();
        List<String> required = parameters == null || parameters.required() == null ? List.of() : parameters.required();
        Map<String, JsonSchemaElement> definitions =
                parameters == null || parameters.definitions() == null ? Map.of() : parameters.definitions();

        return Arrays.stream(inputKeys)
                .map(name -> {
                    JsonSchemaElement schema = properties.get(name);
                    MappedType mappedType = typeOf(schema, definitions, Set.of());
                    boolean optional = schema != null && !required.contains(name) || mappedType.nullable();
                    String description = schema == null ? null : schema.description();
                    if (description != null && description.isBlank()) {
                        description = null;
                    }
                    return new AgentArgument(mappedType.type(), name, null, optional, description);
                })
                .toList();
    }

    static List<AgentArgument> mergeDescriptions(List<AgentArgument> arguments, Map<String, String> inputDescriptions) {
        if (inputDescriptions.isEmpty()) {
            return arguments;
        }

        return arguments.stream()
                .map(argument -> {
                    String description = argument.description();
                    if (description != null && !description.isBlank()) {
                        return argument;
                    }

                    String schemaDescription = inputDescriptions.get(argument.name());
                    if (schemaDescription == null || schemaDescription.isBlank()) {
                        return argument;
                    }

                    return argument.withDescription(schemaDescription);
                })
                .toList();
    }

    private static MappedType typeOf(
            JsonSchemaElement schema, Map<String, JsonSchemaElement> definitions, Set<String> resolvingReferences) {
        if (schema == null) {
            return new MappedType(Object.class, false);
        }
        if (schema instanceof JsonStringSchema || schema instanceof JsonEnumSchema) {
            return new MappedType(String.class, false);
        }
        if (schema instanceof JsonIntegerSchema) {
            return new MappedType(Integer.class, false);
        }
        if (schema instanceof JsonNumberSchema) {
            return new MappedType(Double.class, false);
        }
        if (schema instanceof JsonBooleanSchema) {
            return new MappedType(Boolean.class, false);
        }
        if (schema instanceof JsonArraySchema arraySchema) {
            return new MappedType(
                    parameterizedType(
                            List.class,
                            typeOf(arraySchema.items(), definitions, resolvingReferences)
                                    .type()),
                    false);
        }
        if (schema instanceof JsonObjectSchema) {
            return new MappedType(parameterizedType(Map.class, String.class, Object.class), false);
        }
        if (schema instanceof JsonAnyOfSchema anyOfSchema) {
            return typeOfAnyOf(anyOfSchema, definitions, resolvingReferences);
        }
        if (schema instanceof JsonReferenceSchema referenceSchema) {
            JsonSchemaElement referencedSchema = resolve(referenceSchema, definitions);
            if (referencedSchema == null) {
                return new MappedType(Object.class, false);
            }

            Set<String> nextReferences = new HashSet<>(resolvingReferences);
            if (!nextReferences.add(referenceSchema.reference())) {
                return new MappedType(Object.class, false);
            }
            return typeOf(referencedSchema, definitions, nextReferences);
        }
        if (schema instanceof JsonNullSchema || schema instanceof JsonRawSchema) {
            return new MappedType(Object.class, schema instanceof JsonNullSchema);
        }
        return new MappedType(Object.class, false);
    }

    private static MappedType typeOfAnyOf(
            JsonAnyOfSchema schema, Map<String, JsonSchemaElement> definitions, Set<String> resolvingReferences) {
        List<MappedType> mappedTypes = schema.anyOf().stream()
                .filter(option -> !(option instanceof JsonNullSchema))
                .map(option -> typeOf(option, definitions, resolvingReferences))
                .toList();

        boolean nullable = schema.anyOf().stream().anyMatch(option -> option instanceof JsonNullSchema)
                || mappedTypes.stream().anyMatch(MappedType::nullable);

        if (mappedTypes.isEmpty()) {
            return new MappedType(Object.class, true);
        }

        Type firstType = mappedTypes.get(0).type();
        boolean sameType = mappedTypes.stream().allMatch(mappedType -> Objects.equals(firstType, mappedType.type()));
        // A heterogeneous union has no honest Java common type, so Object is intentional here.
        return new MappedType(sameType ? firstType : Object.class, nullable);
    }

    private static JsonSchemaElement resolve(
            JsonReferenceSchema referenceSchema, Map<String, JsonSchemaElement> definitions) {
        if (definitions.isEmpty() || referenceSchema.reference() == null) {
            return null;
        }

        JsonSchemaElement resolved = definitions.get(referenceSchema.reference());
        if (resolved != null) {
            return resolved;
        }

        String reference = referenceSchema.reference();
        if (reference.startsWith("#/$defs/")) {
            return definitions.get(reference.substring("#/$defs/".length()));
        }
        if (reference.startsWith("#/definitions/")) {
            return definitions.get(reference.substring("#/definitions/".length()));
        }
        return null;
    }

    private record MappedType(Type type, boolean nullable) {}

    private static ParameterizedType parameterizedType(Class<?> rawType, Type... typeArguments) {
        return new ParameterizedTypeImpl(rawType, typeArguments);
    }

    private record ParameterizedTypeImpl(Class<?> rawType, Type[] actualTypeArguments) implements ParameterizedType {

        private ParameterizedTypeImpl {
            actualTypeArguments = actualTypeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ParameterizedType that)) {
                return false;
            }
            return Objects.equals(rawType, that.getRawType())
                    && Objects.equals(getOwnerType(), that.getOwnerType())
                    && Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(rawType);
        }

        @Override
        public String toString() {
            return rawType.getTypeName() + "<"
                    + Arrays.stream(actualTypeArguments).map(Type::getTypeName).collect(Collectors.joining(", "))
                    + ">";
        }
    }
}
