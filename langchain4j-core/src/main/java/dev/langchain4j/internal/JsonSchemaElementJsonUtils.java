package dev.langchain4j.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts between {@link JsonSchemaElement} and JSON Schema {@link Map} representation
 * with round-trip fidelity.
 * <p>
 * Unlike {@link JsonSchemaElementUtils#toMap(JsonSchemaElement)}, which is optimized for
 * LLM provider APIs and intentionally omits fields like {@code additionalProperties} in
 * non-strict mode, this class preserves all fields needed for lossless serialization and
 * deserialization.
 *
 * @see JsonSchemaElementUtils
 */
@Internal
public class JsonSchemaElementJsonUtils {

    // Allowed keys per schema type — used by fromMap() to detect extra keywords
    // that cannot be represented by the corresponding JsonSchemaElement subtype.
    // When a map contains keys outside this set, fromMap() falls back to JsonRawSchema.
    // NOTE: When adding new JsonSchemaElement subtypes, update these sets accordingly.
    private static final Set<String> STRING_KEYS = Set.of("type", "description");
    private static final Set<String> INTEGER_KEYS = Set.of("type", "description");
    private static final Set<String> NUMBER_KEYS = Set.of("type", "description");
    private static final Set<String> BOOLEAN_KEYS = Set.of("type", "description");
    private static final Set<String> NULL_KEYS = Set.of("type");
    private static final Set<String> OBJECT_KEYS =
            Set.of("type", "description", "properties", "required", "additionalProperties", "$defs");
    private static final Set<String> ARRAY_KEYS = Set.of("type", "description", "items");
    private static final Set<String> ENUM_KEYS = Set.of("type", "description", "enum");
    private static final Set<String> ANYOF_KEYS = Set.of("anyOf", "description");
    private static final Set<String> REF_KEYS = Set.of("$ref");

    private JsonSchemaElementJsonUtils() {}

    // ---- toMap: JsonSchemaElement -> Map<String, Object> ----

    /**
     * Converts a {@link JsonSchemaElement} to a standard JSON Schema {@link Map} representation.
     */
    public static Map<String, Object> toMap(JsonSchemaElement element) {
        ensureNotNull(element, "element");

        // Composite types: own implementation (recursive, preserves additionalProperties / null items)
        if (element instanceof JsonObjectSchema obj) return objectSchemaToMap(obj);
        if (element instanceof JsonArraySchema arr) return arraySchemaToMap(arr);
        if (element instanceof JsonAnyOfSchema anyOf) return anyOfSchemaToMap(anyOf);

        // Enum: type + description + enum values
        if (element instanceof JsonEnumSchema en) {
            Map<String, Object> map = typedSchema("string", en.description());
            map.put("enum", new ArrayList<>(en.enumValues()));
            return map;
        }

        // Simple typed schemas: type + description
        if (element instanceof JsonStringSchema s) return typedSchema("string", s.description());
        if (element instanceof JsonIntegerSchema i) return typedSchema("integer", i.description());
        if (element instanceof JsonNumberSchema n) return typedSchema("number", n.description());
        if (element instanceof JsonBooleanSchema b) return typedSchema("boolean", b.description());
        if (element instanceof JsonNullSchema) return typedSchema("null", null);

        // Reference
        if (element instanceof JsonReferenceSchema ref) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (ref.reference() != null) {
                map.put("$ref", "#/$defs/" + ref.reference());
            }
            return map;
        }

        // Raw fallback
        if (element instanceof JsonRawSchema raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = Json.fromJson(raw.schema(), Map.class);
            return map;
        }

        throw new IllegalArgumentException("Unknown JsonSchemaElement type: " + element.getClass());
    }

    private static Map<String, Object> typedSchema(String type, String description) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        if (description != null) {
            map.put("description", description);
        }
        return map;
    }

    private static Map<String, Object> objectSchemaToMap(JsonObjectSchema obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");
        if (obj.description() != null) {
            map.put("description", obj.description());
        }
        if (obj.properties() != null && !obj.properties().isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            obj.properties().forEach((name, schema) -> properties.put(name, toMap(schema)));
            map.put("properties", properties);
        }
        if (obj.required() != null && !obj.required().isEmpty()) {
            map.put("required", new ArrayList<>(obj.required()));
        }
        if (obj.additionalProperties() != null) {
            map.put("additionalProperties", obj.additionalProperties());
        }
        if (obj.definitions() != null && !obj.definitions().isEmpty()) {
            Map<String, Object> defs = new LinkedHashMap<>();
            obj.definitions().forEach((name, schema) -> defs.put(name, toMap(schema)));
            map.put("$defs", defs);
        }
        return map;
    }

    private static Map<String, Object> arraySchemaToMap(JsonArraySchema arr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "array");
        if (arr.description() != null) {
            map.put("description", arr.description());
        }
        if (arr.items() != null) {
            map.put("items", toMap(arr.items()));
        }
        return map;
    }

    private static Map<String, Object> anyOfSchemaToMap(JsonAnyOfSchema anyOf) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (anyOf.description() != null) {
            map.put("description", anyOf.description());
        }
        List<Map<String, Object>> schemas = new ArrayList<>(anyOf.anyOf().size());
        anyOf.anyOf().forEach(s -> schemas.add(toMap(s)));
        map.put("anyOf", schemas);
        return map;
    }

    // ---- fromMap: Map<String, Object> -> JsonSchemaElement ----

    /**
     * Converts a standard JSON Schema {@link Map} representation back to a {@link JsonSchemaElement}.
     * <p>
     * Only the subset of JSON Schema expressible by {@link JsonSchemaElement} subtypes is supported.
     * When a map contains additional JSON Schema keywords (e.g., {@code format}, {@code pattern},
     * {@code minimum}, schema-valued {@code additionalProperties}) that cannot be represented by
     * the corresponding typed schema, the entire node falls back to {@link JsonRawSchema} to
     * preserve round-trip fidelity. The fallback granularity is per-node: a parent
     * {@link JsonObjectSchema} can still be typed even if a child property falls back to raw.
     *
     * @throws IllegalArgumentException if the map contains structurally invalid values
     *                                  (e.g., {@code $ref} is not a string, {@code anyOf} is not a list,
     *                                  {@code properties} contains a non-object value)
     */
    @SuppressWarnings("unchecked")
    public static JsonSchemaElement fromMap(Map<String, Object> map) {
        ensureNotNull(map, "map");

        // $ref
        if (map.containsKey("$ref")) {
            Object refObj = map.get("$ref");
            if (!(refObj instanceof String ref)) {
                throw new IllegalArgumentException("\"$ref\" must be a string, but was: " + className(refObj));
            }
            if (!ref.startsWith("#/$defs/") || !isRepresentable(map, REF_KEYS)) {
                return rawFallback(map);
            }
            String reference = ref.substring("#/$defs/".length());
            return JsonReferenceSchema.builder().reference(reference).build();
        }

        // anyOf
        if (map.containsKey("anyOf")) {
            Object anyOfObj = map.get("anyOf");
            if (!(anyOfObj instanceof List)) {
                throw new IllegalArgumentException("\"anyOf\" must be a list, but was: " + className(anyOfObj));
            }
            if (!isRepresentable(map, ANYOF_KEYS)) {
                return rawFallback(map);
            }
            List<?> anyOfList = (List<?>) anyOfObj;
            List<JsonSchemaElement> anyOf = new ArrayList<>(anyOfList.size());
            for (Object element : anyOfList) {
                if (!(element instanceof Map)) {
                    throw new IllegalArgumentException(
                            "\"anyOf\" elements must be JSON objects, but found: " + className(element));
                }
                anyOf.add(fromMap((Map<String, Object>) element));
            }
            return JsonAnyOfSchema.builder()
                    .description(optionalString(map, "description"))
                    .anyOf(anyOf)
                    .build();
        }

        // enum
        if (map.containsKey("enum")) {
            Object enumObj = map.get("enum");
            if (!(enumObj instanceof List<?> enumList)) {
                throw new IllegalArgumentException("\"enum\" must be a list, but was: " + className(enumObj));
            }
            // JsonEnumSchema always serializes as type:"string", so only match when
            // type is absent or explicitly "string" to avoid silent schema rewriting.
            // Note: isRepresentable() already rejects null values (e.g., type:null).
            Object enumTypeObj = map.get("type");
            if (!isRepresentable(map, ENUM_KEYS)
                    || !allStrings(enumList)
                    || (enumTypeObj != null && !"string".equals(enumTypeObj))) {
                return rawFallback(map);
            }
            List<String> enumValues = requireStringList("enum", enumList);
            return JsonEnumSchema.builder()
                    .description(optionalString(map, "description"))
                    .enumValues(enumValues)
                    .build();
        }

        // type-based dispatch
        Object typeObj = map.get("type");
        if (!(typeObj instanceof String type)) {
            return rawFallback(map);
        }

        return switch (type) {
            case "string" ->
                isRepresentable(map, STRING_KEYS)
                        ? JsonStringSchema.builder()
                                .description(optionalString(map, "description"))
                                .build()
                        : rawFallback(map);
            case "integer" ->
                isRepresentable(map, INTEGER_KEYS)
                        ? JsonIntegerSchema.builder()
                                .description(optionalString(map, "description"))
                                .build()
                        : rawFallback(map);
            case "number" ->
                isRepresentable(map, NUMBER_KEYS)
                        ? JsonNumberSchema.builder()
                                .description(optionalString(map, "description"))
                                .build()
                        : rawFallback(map);
            case "boolean" ->
                isRepresentable(map, BOOLEAN_KEYS)
                        ? JsonBooleanSchema.builder()
                                .description(optionalString(map, "description"))
                                .build()
                        : rawFallback(map);
            case "null" -> isRepresentable(map, NULL_KEYS) ? new JsonNullSchema() : rawFallback(map);
            case "object" -> {
                if (!isRepresentable(map, OBJECT_KEYS)) {
                    yield rawFallback(map);
                }
                // schema-valued additionalProperties (e.g., {"type":"string"}) is not representable
                Object additionalProps = map.get("additionalProperties");
                if (additionalProps != null && !(additionalProps instanceof Boolean)) {
                    yield rawFallback(map);
                }

                JsonObjectSchema.Builder builder =
                        JsonObjectSchema.builder().description(optionalString(map, "description"));

                Object propertiesObj = map.get("properties");
                if (propertiesObj instanceof Map) {
                    Map<String, Object> properties = (Map<String, Object>) propertiesObj;
                    Map<String, JsonSchemaElement> schemaProperties = new LinkedHashMap<>();
                    properties.forEach((name, propValue) -> {
                        if (!(propValue instanceof Map)) {
                            throw new IllegalArgumentException("Property \"" + name
                                    + "\" must be a JSON object, but was: " + className(propValue));
                        }
                        schemaProperties.put(name, fromMap((Map<String, Object>) propValue));
                    });
                    builder.addProperties(schemaProperties);
                } else if (propertiesObj != null) {
                    throw new IllegalArgumentException(
                            "\"properties\" must be a JSON object, but was: " + className(propertiesObj));
                }

                Object requiredObj = map.get("required");
                if (requiredObj instanceof List) {
                    builder.required(requireStringList("required", (List<?>) requiredObj));
                } else if (requiredObj != null) {
                    throw new IllegalArgumentException(
                            "\"required\" must be a list, but was: " + className(requiredObj));
                }

                if (additionalProps instanceof Boolean) {
                    builder.additionalProperties((Boolean) additionalProps);
                }

                Object defsObj = map.get("$defs");
                if (defsObj instanceof Map) {
                    Map<String, Object> defs = (Map<String, Object>) defsObj;
                    Map<String, JsonSchemaElement> definitions = new LinkedHashMap<>();
                    defs.forEach((name, defValue) -> {
                        if (!(defValue instanceof Map)) {
                            throw new IllegalArgumentException("Definition \"" + name
                                    + "\" must be a JSON object, but was: " + className(defValue));
                        }
                        definitions.put(name, fromMap((Map<String, Object>) defValue));
                    });
                    builder.definitions(definitions);
                } else if (defsObj != null) {
                    throw new IllegalArgumentException(
                            "\"$defs\" must be a JSON object, but was: " + className(defsObj));
                }

                yield builder.build();
            }
            case "array" -> {
                if (!isRepresentable(map, ARRAY_KEYS)) {
                    yield rawFallback(map);
                }

                JsonArraySchema.Builder builder =
                        JsonArraySchema.builder().description(optionalString(map, "description"));

                Object itemsObj = map.get("items");
                if (itemsObj instanceof Map) {
                    builder.items(fromMap((Map<String, Object>) itemsObj));
                } else if (itemsObj != null) {
                    throw new IllegalArgumentException(
                            "\"items\" must be a JSON object, but was: " + className(itemsObj));
                }

                yield builder.build();
            }
            default -> rawFallback(map);
        };
    }

    // ---- helpers ----

    private static boolean isRepresentable(Map<String, Object> map, Set<String> allowedKeys) {
        // Typed models drop null values (e.g., description:null → absent), so any
        // explicit null in the map makes it unrepresentable for round-trip fidelity.
        // Note: Map.of() throws NPE on containsValue(null), so we iterate instead.
        return allowedKeys.containsAll(map.keySet()) && !hasNullValue(map);
    }

    private static boolean hasNullValue(Map<String, Object> map) {
        for (Object value : map.values()) {
            if (value == null) return true;
        }
        return false;
    }

    private static JsonRawSchema rawFallback(Map<String, Object> map) {
        return JsonRawSchema.from(Json.toJson(map));
    }

    private static boolean allStrings(List<?> list) {
        return list.stream().allMatch(String.class::isInstance);
    }

    private static String optionalString(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("\"" + field + "\" must be a string, but was: " + className(value));
        }
        return (String) value;
    }

    private static String className(Object obj) {
        return obj == null ? "null" : obj.getClass().getSimpleName();
    }

    private static List<String> requireStringList(String fieldName, List<?> list) {
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof String)) {
                throw new IllegalArgumentException(
                        "\"" + fieldName + "\" elements must be strings, but found: " + className(element));
            }
            result.add((String) element);
        }
        return result;
    }
}
