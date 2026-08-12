package dev.langchain4j.internal;

import static dev.langchain4j.internal.PolymorphicTypes.discriminatorPropertyName;
import static dev.langchain4j.internal.PolymorphicTypes.discriminatorValue;
import static dev.langchain4j.internal.PolymorphicTypes.findConcreteSubtypes;
import static dev.langchain4j.internal.PolymorphicTypes.isPolymorphic;
import static dev.langchain4j.internal.PolymorphicTypes.verifyJsonTypeInfoIsSupported;
import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Arrays.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.Internal;
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
import dev.langchain4j.model.output.structured.Description;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Internal
public class JsonSchemaElementUtils {

    private static final String DEFAULT_UUID_DESCRIPTION = "String in a UUID format";

    public static JsonSchemaElement jsonSchemaElementFrom(Class<?> clazz) {
        return jsonSchemaElementFrom(clazz, clazz, null, false, new LinkedHashMap<>());
    }

    public static JsonSchemaElement jsonSchemaElementFrom(
            Class<?> clazz,
            Type type,
            String fieldDescription,
            boolean areSubFieldsRequiredByDefault,
            Map<Class<?>, VisitedClassMetadata> visited) {
        if (isJsonString(clazz)) {
            return JsonStringSchema.builder()
                    .description(Optional.ofNullable(fieldDescription).orElse(descriptionFrom(clazz)))
                    .build();
        }

        if (isJsonInteger(clazz)) {
            return JsonIntegerSchema.builder().description(fieldDescription).build();
        }

        if (isJsonNumber(clazz)) {
            return JsonNumberSchema.builder().description(fieldDescription).build();
        }

        if (isJsonBoolean(clazz)) {
            return JsonBooleanSchema.builder().description(fieldDescription).build();
        }

        if (clazz.isEnum()) {
            return JsonEnumSchema.builder()
                    .enumValues(stream(clazz.getEnumConstants())
                            .map(e -> ((Enum<?>) e).name())
                            .toList())
                    .description(Optional.ofNullable(fieldDescription).orElse(descriptionFrom(clazz)))
                    .build();
        }

        if (clazz.isArray()) {
            return JsonArraySchema.builder()
                    .items(jsonSchemaElementFrom(
                            clazz.getComponentType(), null, null, areSubFieldsRequiredByDefault, visited))
                    .description(fieldDescription)
                    .build();
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            Type elementType = collectionElementType(type);
            return JsonArraySchema.builder()
                    .items(jsonSchemaElementFrom(
                            rawClassOf(elementType), elementType, null, areSubFieldsRequiredByDefault, visited))
                    .description(fieldDescription)
                    .build();
        }

        if (isPolymorphic(clazz)) {
            return polymorphicSchemaFrom(clazz, fieldDescription, areSubFieldsRequiredByDefault, visited);
        }

        return jsonObjectOrReferenceSchemaFrom(clazz, fieldDescription, areSubFieldsRequiredByDefault, visited, false);
    }

    public static JsonSchemaElement polymorphicSchemaFrom(
            Class<?> baseType,
            String description,
            boolean areSubFieldsRequiredByDefault,
            Map<Class<?>, VisitedClassMetadata> visited) {
        verifyJsonTypeInfoIsSupported(baseType);

        if (visited.containsKey(baseType)) {
            VisitedClassMetadata metadata = visited.get(baseType);
            metadata.recursionDetected = true;
            return JsonReferenceSchema.builder().reference(metadata.reference).build();
        }

        String reference = generateUUIDFrom(baseType.getName());
        VisitedClassMetadata metadata = new VisitedClassMetadata(
                JsonReferenceSchema.builder().reference(reference).build(), reference, false);
        visited.put(baseType, metadata);

        String discriminatorProperty = discriminatorPropertyName(baseType);
        List<JsonSchemaElement> options = new ArrayList<>();
        for (Class<?> subtype : findConcreteSubtypes(baseType)) {
            JsonSchemaElement subtypeSchema =
                    jsonObjectOrReferenceSchemaFrom(subtype, null, areSubFieldsRequiredByDefault, visited, false);
            JsonSchemaElement withDiscriminator =
                    addDiscriminator(subtypeSchema, baseType, subtype, discriminatorProperty);
            options.add(withDiscriminator);
            // Refresh the cached entry so any recursive $ref to this subtype keeps the discriminator.
            VisitedClassMetadata subtypeMetadata = visited.get(subtype);
            if (subtypeMetadata != null) {
                subtypeMetadata.jsonSchemaElement = withDiscriminator;
            }
        }
        String desc = description != null
                ? description
                : Optional.ofNullable(descriptionFrom(baseType)).orElse(baseType.getSimpleName());
        JsonAnyOfSchema anyOf =
                JsonAnyOfSchema.builder().description(desc).anyOf(options).build();
        metadata.jsonSchemaElement = anyOf;
        return anyOf;
    }

    private static JsonSchemaElement addDiscriminator(
            JsonSchemaElement subtypeSchema, Class<?> baseType, Class<?> subtype, String discriminatorProperty) {
        if (!(subtypeSchema instanceof JsonObjectSchema obj)) {
            return subtypeSchema;
        }

        String discriminatorValue = discriminatorValue(baseType, subtype);

        // Idempotency: a recursive call may have already augmented this subtype.
        if (obj.properties().get(discriminatorProperty) instanceof JsonEnumSchema existing
                && existing.enumValues() != null
                && existing.enumValues().size() == 1
                && discriminatorValue.equals(existing.enumValues().get(0))) {
            return obj;
        }

        if (obj.properties().containsKey(discriminatorProperty)) {
            JsonTypeInfo info = baseType.getAnnotation(JsonTypeInfo.class);
            // The discriminator field is allowed to coexist with a same-named bean field only when
            // @JsonTypeInfo(visible=true) or @JsonTypeInfo(include=As.EXISTING_PROPERTY).
            boolean allowed = info != null && (info.visible() || info.include() == JsonTypeInfo.As.EXISTING_PROPERTY);
            if (!allowed) {
                throw new IllegalArgumentException(String.format(
                        "Polymorphic subtype %s declares a field named '%s', which collides with the discriminator "
                                + "property used for %s. Either rename the field, specify a different discriminator "
                                + "name with @JsonTypeInfo(property = \"...\") on %s, set @JsonTypeInfo(visible = true), "
                                + "or use @JsonTypeInfo(include = As.EXISTING_PROPERTY) if the field is intentionally "
                                + "part of the subtype.",
                        subtype.getName(), discriminatorProperty, baseType.getName(), baseType.getName()));
            }
        }

        Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        properties.put(
                discriminatorProperty,
                JsonEnumSchema.builder().enumValues(discriminatorValue).build());
        obj.properties().forEach(properties::putIfAbsent);

        List<String> required = new ArrayList<>();
        required.add(discriminatorProperty);
        obj.required().forEach(r -> {
            if (!required.contains(r)) required.add(r);
        });

        return JsonObjectSchema.builder()
                .description(Optional.ofNullable(obj.description()).orElse(subtype.getSimpleName()))
                .addProperties(properties)
                .required(required)
                .additionalProperties(obj.additionalProperties())
                .build();
    }

    /**
     * If recursion was detected for {@code baseType}, returns a {@link JsonReferenceSchema} to the
     * polymorphic body (which will be emitted under {@code $defs}); otherwise returns
     * {@code element} unchanged. Avoids duplicating the body inline next to the {@code $defs} entry.
     */
    public static JsonSchemaElement referenceIfRecursive(
            JsonSchemaElement element, Class<?> baseType, Map<Class<?>, VisitedClassMetadata> visited) {
        VisitedClassMetadata metadata = visited.get(baseType);
        if (metadata != null && metadata.recursionDetected && element instanceof JsonAnyOfSchema) {
            return JsonReferenceSchema.builder().reference(metadata.reference).build();
        }
        return element;
    }

    /**
     * Wraps {@code element} as the only required property of an object schema (the
     * {@code value}/{@code values} envelope used at the root of polymorphic AI Service return types,
     * since {@code anyOf} is not allowed at the JSON-schema root) and attaches any
     * recursion-induced definitions collected in {@code visited}.
     */
    public static JsonObjectSchema wrapPolymorphic(
            String propertyName, JsonSchemaElement element, Map<Class<?>, VisitedClassMetadata> visited) {
        JsonObjectSchema.Builder builder =
                JsonObjectSchema.builder().addProperty(propertyName, element).required(propertyName);
        Map<String, JsonSchemaElement> definitions = new LinkedHashMap<>();
        visited.forEach((clazz, meta) -> {
            if (meta.recursionDetected) {
                definitions.put(meta.reference, meta.jsonSchemaElement);
            }
        });
        if (!definitions.isEmpty()) {
            builder.definitions(definitions);
        }
        return builder.build();
    }

    public static JsonSchemaElement jsonObjectOrReferenceSchemaFrom(
            Class<?> type,
            String description,
            boolean areSubFieldsRequiredByDefault,
            Map<Class<?>, VisitedClassMetadata> visited,
            boolean setDefinitions) {
        if (visited.containsKey(type) && isCustomClass(type)) {
            VisitedClassMetadata visitedClassMetadata = visited.get(type);
            JsonSchemaElement jsonSchemaElement = visitedClassMetadata.jsonSchemaElement;
            if (jsonSchemaElement instanceof JsonReferenceSchema) {
                visitedClassMetadata.recursionDetected = true;
            }
            if (jsonSchemaElement instanceof JsonObjectSchema obj) {
                if (Objects.equals(description, obj.description())) {
                    return obj;
                }
                return obj.toBuilder().description(description).build();
            }

            return jsonSchemaElement;
        }

        String reference = generateUUIDFrom(type.getName());
        JsonReferenceSchema jsonReferenceSchema =
                JsonReferenceSchema.builder().reference(reference).build();
        visited.put(type, new VisitedClassMetadata(jsonReferenceSchema, reference, false));

        Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            String fieldName = field.getName();
            if (isStatic(field.getModifiers()) || fieldName.equals("__$hits$__") || fieldName.startsWith("this$")) {
                continue;
            }
            if (isRequired(field, areSubFieldsRequiredByDefault)) {
                required.add(fieldName);
            }
            String fieldDescription = descriptionFrom(field);
            JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(
                    field.getType(), field.getGenericType(), fieldDescription, areSubFieldsRequiredByDefault, visited);
            properties.put(fieldName, jsonSchemaElement);
        }

        JsonObjectSchema.Builder builder = JsonObjectSchema.builder()
                .description(Optional.ofNullable(description).orElse(descriptionFrom(type)))
                .addProperties(properties)
                .required(required);

        visited.get(type).jsonSchemaElement = builder.build();

        if (setDefinitions) {
            Map<String, JsonSchemaElement> definitions = new LinkedHashMap<>();
            visited.forEach((clazz, visitedClassMetadata) -> {
                if (visitedClassMetadata.recursionDetected) {
                    definitions.put(visitedClassMetadata.reference, visitedClassMetadata.jsonSchemaElement);
                }
            });
            if (!definitions.isEmpty()) {
                builder.definitions(definitions);
            }
        }

        return builder.build();
    }

    private static boolean isRequired(Field field, boolean defaultValue) {
        JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        if (jsonProperty != null) {
            return jsonProperty.required();
        }

        return defaultValue;
    }

    private static String descriptionFrom(Field field) {
        return descriptionFrom(field.getAnnotation(Description.class));
    }

    private static String descriptionFrom(Class<?> type) {
        if (type == UUID.class) {
            return DEFAULT_UUID_DESCRIPTION;
        }
        return descriptionFrom(type.getAnnotation(Description.class));
    }

    private static String descriptionFrom(Description description) {
        if (description == null) {
            return null;
        }
        return String.join(" ", description.value());
    }

    /**
     * Returns the single type argument of a {@code Collection<E>}-shaped type, preserving its full
     * generic shape so callers can recurse into nested generics like {@code List<List<X>>} or {@code List<Foo<Bar>>}.
     */
    private static Type collectionElementType(Type type) {
        if (type instanceof final ParameterizedType parameterizedType) {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                return actualTypeArguments[0];
            }
        }
        return null;
    }

    /**
     * Reduces an arbitrary {@link Type} to a usable {@link Class}.
     * Handles the common cases that arise when walking generic field types, e.g. a
     * {@code Collection<X<Y>>} field has an actual type argument of {@code X<Y>}, which is a
     * {@link ParameterizedType}, not a {@link Class}.
     */
    private static Class<?> rawClassOf(Type type) {
        if (type == null) {
            return Object.class;
        }
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0) {
                return rawClassOf(upperBounds[0]);
            }
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            Type[] bounds = typeVariable.getBounds();
            if (bounds.length > 0) {
                return rawClassOf(bounds[0]);
            }
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return rawClassOf(genericArrayType.getGenericComponentType()).arrayType();
        }
        return Object.class;
    }

    static boolean isCustomClass(Class<?> clazz) {
        if (clazz.getPackage() != null) {
            String packageName = clazz.getPackage().getName();
            if (packageName.startsWith("java.")
                    || packageName.startsWith("javax.")
                    || packageName.startsWith("jdk.")
                    || packageName.startsWith("sun.")
                    || packageName.startsWith("com.sun.")) {
                return false;
            }
        }

        return true;
    }

    public static Map<String, Map<String, Object>> toMap(Map<String, JsonSchemaElement> properties) {
        return toMap(properties, false);
    }

    public static Map<String, Map<String, Object>> toMap(Map<String, JsonSchemaElement> properties, boolean strict) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        properties.forEach((property, value) -> map.put(property, toMap(value, strict)));
        return map;
    }

    public static Map<String, Object> toMap(JsonSchemaElement jsonSchemaElement) {
        return toMap(jsonSchemaElement, false);
    }

    public static Map<String, Object> toMap(JsonSchemaElement jsonSchemaElement, boolean strict) {
        return toMap(jsonSchemaElement, strict, true);
    }

    public static Map<String, Object> toMap(JsonSchemaElement jsonSchemaElement, boolean strict, boolean required) {
        return toMap(jsonSchemaElement, strict, required, null);
    }

    public static Map<String, Object> toMap(
            JsonSchemaElement jsonSchemaElement, boolean strict, boolean required, String enumType) {
        if (jsonSchemaElement instanceof JsonObjectSchema jsonObjectSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type("object", strict, required));

            if (jsonObjectSchema.description() != null) {
                map.put("description", jsonObjectSchema.description());
            }

            Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
            jsonObjectSchema
                    .properties()
                    .forEach((property, value) -> properties.put(
                            property,
                            toMap(value, strict, jsonObjectSchema.required().contains(property), enumType)));
            map.put("properties", properties);

            if (strict) {
                // When using Structured Outputs with strict=true, all fields must be required.
                // See
                // https://platform.openai.com/docs/guides/structured-outputs/supported-schemas?api-mode=chat#all-fields-must-be-required
                map.put(
                        "required",
                        jsonObjectSchema.properties().keySet().stream().toList());
            } else {
                if (jsonObjectSchema.required() != null) {
                    map.put("required", jsonObjectSchema.required());
                }
            }

            if (strict) {
                map.put("additionalProperties", false);
            }

            if (!jsonObjectSchema.definitions().isEmpty()) {
                map.put("$defs", toMap(jsonObjectSchema.definitions(), strict));
            }

            return map;
        } else if (jsonSchemaElement instanceof JsonArraySchema jsonArraySchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type("array", strict, required));
            if (jsonArraySchema.description() != null) {
                map.put("description", jsonArraySchema.description());
            }
            if (jsonArraySchema.items() != null) {
                map.put("items", toMap(jsonArraySchema.items(), strict));
            } else {
                map.put("items", Collections.emptyMap());
            }
            return map;
        } else if (jsonSchemaElement instanceof JsonEnumSchema jsonEnumSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (enumType != null) {
                map.put("type", enumType);
            } else {
                map.put("type", type("string", strict, required));
            }
            if (jsonEnumSchema.description() != null) {
                map.put("description", jsonEnumSchema.description());
            }
            map.put("enum", jsonEnumSchema.enumValues());
            return map;
        } else if (jsonSchemaElement instanceof JsonStringSchema jsonStringSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type("string", strict, required));
            if (jsonStringSchema.description() != null) {
                map.put("description", jsonStringSchema.description());
            }
            return map;
        } else if (jsonSchemaElement instanceof JsonIntegerSchema jsonIntegerSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type("integer", strict, required));
            if (jsonIntegerSchema.description() != null) {
                map.put("description", jsonIntegerSchema.description());
            }
            return map;
        } else if (jsonSchemaElement instanceof JsonNumberSchema jsonNumberSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type("number", strict, required));
            if (jsonNumberSchema.description() != null) {
                map.put("description", jsonNumberSchema.description());
            }
            return map;
        } else if (jsonSchemaElement instanceof JsonBooleanSchema jsonBooleanSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type("boolean", strict, required));
            if (jsonBooleanSchema.description() != null) {
                map.put("description", jsonBooleanSchema.description());
            }
            return map;
        } else if (jsonSchemaElement instanceof JsonReferenceSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            String reference = ((JsonReferenceSchema) jsonSchemaElement).reference();
            if (reference != null) {
                map.put("$ref", "#/$defs/" + reference);
            }
            return map;
        } else if (jsonSchemaElement instanceof JsonAnyOfSchema jsonAnyOfSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (jsonAnyOfSchema.description() != null) {
                map.put("description", jsonAnyOfSchema.description());
            }
            List<Map<String, Object>> anyOf = jsonAnyOfSchema.anyOf().stream()
                    .map(element -> toMap(element, strict))
                    .collect(Collectors.toList());
            map.put("anyOf", anyOf);
            return map;
        } else if (jsonSchemaElement instanceof JsonNullSchema) {
            return Map.of("type", "null");
        } else if (jsonSchemaElement instanceof JsonRawSchema jsonNative) {
            @SuppressWarnings("unchecked")
            var map = (Map<String, Object>) Json.fromJson(jsonNative.schema(), Map.class);
            return map;
        } else {
            throw new IllegalArgumentException("Unknown type: " + jsonSchemaElement.getClass());
        }
    }

    private static Object type(String type, boolean strict, boolean required) {
        if (strict && !required) {
            // Emulating an optional parameter by using a union type with null.
            // See
            // https://platform.openai.com/docs/guides/structured-outputs/supported-schemas?api-mode=chat#all-fields-must-be-required
            return new String[] {type, "null"};
        } else {
            return type;
        }
    }

    static boolean isJsonInteger(Class<?> type) {
        return type == byte.class
                || type == Byte.class
                || type == short.class
                || type == Short.class
                || type == int.class
                || type == Integer.class
                || type == long.class
                || type == Long.class
                || type == BigInteger.class;
    }

    static boolean isJsonNumber(Class<?> type) {
        return type == float.class
                || type == Float.class
                || type == double.class
                || type == Double.class
                || type == BigDecimal.class;
    }

    static boolean isJsonBoolean(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    static boolean isJsonString(Class<?> type) {
        return type == String.class
                || type == char.class
                || type == Character.class
                || CharSequence.class.isAssignableFrom(type)
                || type == UUID.class;
    }

    static boolean isJsonArray(Class<?> type) {
        return type.isArray() || Iterable.class.isAssignableFrom(type);
    }

    public static class VisitedClassMetadata {

        public JsonSchemaElement jsonSchemaElement;
        public String reference;
        public boolean recursionDetected;

        public VisitedClassMetadata(JsonSchemaElement jsonSchemaElement, String reference, boolean recursionDetected) {
            this.jsonSchemaElement = jsonSchemaElement;
            this.reference = reference;
            this.recursionDetected = recursionDetected;
        }
    }
}
