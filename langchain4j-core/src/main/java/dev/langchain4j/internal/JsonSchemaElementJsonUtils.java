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

/**
 * Converts between {@link JsonSchemaElement} and JSON Schema {@link Map} representation.
 *
 * @see JsonSchemaElementUtils
 */
@Internal
public class JsonSchemaElementJsonUtils {

    private JsonSchemaElementJsonUtils() {}

    public static Map<String, Object> toMap(JsonSchemaElement element) {
        ensureNotNull(element, "element");

        if (element instanceof JsonObjectSchema obj) {
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
        } else if (element instanceof JsonArraySchema arr) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "array");
            if (arr.description() != null) {
                map.put("description", arr.description());
            }
            if (arr.items() != null) {
                map.put("items", toMap(arr.items()));
            }
            return map;
        } else if (element instanceof JsonEnumSchema en) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "string");
            if (en.description() != null) {
                map.put("description", en.description());
            }
            map.put("enum", new ArrayList<>(en.enumValues()));
            return map;
        } else if (element instanceof JsonStringSchema str) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "string");
            if (str.description() != null) {
                map.put("description", str.description());
            }
            return map;
        } else if (element instanceof JsonIntegerSchema intSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "integer");
            if (intSchema.description() != null) {
                map.put("description", intSchema.description());
            }
            return map;
        } else if (element instanceof JsonNumberSchema num) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "number");
            if (num.description() != null) {
                map.put("description", num.description());
            }
            return map;
        } else if (element instanceof JsonBooleanSchema bool) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "boolean");
            if (bool.description() != null) {
                map.put("description", bool.description());
            }
            return map;
        } else if (element instanceof JsonNullSchema) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "null");
            return map;
        } else if (element instanceof JsonReferenceSchema ref) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (ref.reference() != null) {
                map.put("$ref", "#/$defs/" + ref.reference());
            }
            return map;
        } else if (element instanceof JsonAnyOfSchema anyOf) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (anyOf.description() != null) {
                map.put("description", anyOf.description());
            }
            List<Map<String, Object>> schemas = new ArrayList<>(anyOf.anyOf().size());
            anyOf.anyOf().forEach(s -> schemas.add(toMap(s)));
            map.put("anyOf", schemas);
            return map;
        } else if (element instanceof JsonRawSchema raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = Json.fromJson(raw.schema(), Map.class);
            return map;
        } else {
            throw new IllegalArgumentException("Unknown JsonSchemaElement type: " + element.getClass());
        }
    }

    /**
     * Converts a standard JSON Schema {@link Map} representation back to a {@link JsonSchemaElement}.
     * <p>
     * Only the subset of JSON Schema expressible by {@link JsonSchemaElement} subtypes is supported.
     * Additional JSON Schema keywords (e.g., {@code format}, {@code pattern}, schema-valued
     * {@code additionalProperties}) are not preserved. Falls back to {@link JsonRawSchema}
     * for unrecognized schema structures (unknown {@code type}, missing {@code type}).
     *
     * @throws IllegalArgumentException if the map contains values of unexpected types
     *                                  (e.g., {@code $ref} is not a string, {@code anyOf} is not a list)
     */
    @SuppressWarnings("unchecked")
    public static JsonSchemaElement fromMap(Map<String, Object> map) {
        ensureNotNull(map, "map");

        if (map.containsKey("$ref")) {
            Object refObj = map.get("$ref");
            if (!(refObj instanceof String ref)) {
                throw new IllegalArgumentException("\"$ref\" must be a string, but was: " + className(refObj));
            }
            if (!ref.startsWith("#/$defs/")) {
                return JsonRawSchema.from(Json.toJson(map));
            }
            String reference = ref.substring("#/$defs/".length());
            return JsonReferenceSchema.builder().reference(reference).build();
        }

        if (map.containsKey("anyOf")) {
            Object anyOfObj = map.get("anyOf");
            if (!(anyOfObj instanceof List)) {
                throw new IllegalArgumentException("\"anyOf\" must be a list, but was: " + className(anyOfObj));
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
                    .description((String) map.get("description"))
                    .anyOf(anyOf)
                    .build();
        }

        if (map.containsKey("enum")) {
            Object enumObj = map.get("enum");
            if (!(enumObj instanceof List)) {
                throw new IllegalArgumentException("\"enum\" must be a list, but was: " + className(enumObj));
            }
            List<String> enumValues = requireStringList("enum", (List<?>) enumObj);
            return JsonEnumSchema.builder()
                    .description((String) map.get("description"))
                    .enumValues(enumValues)
                    .build();
        }

        Object typeObj = map.get("type");
        if (!(typeObj instanceof String type)) {
            return JsonRawSchema.from(Json.toJson(map));
        }

        return switch (type) {
            case "string" ->
                JsonStringSchema.builder()
                        .description((String) map.get("description"))
                        .build();
            case "integer" ->
                JsonIntegerSchema.builder()
                        .description((String) map.get("description"))
                        .build();
            case "number" ->
                JsonNumberSchema.builder()
                        .description((String) map.get("description"))
                        .build();
            case "boolean" ->
                JsonBooleanSchema.builder()
                        .description((String) map.get("description"))
                        .build();
            case "null" -> new JsonNullSchema();
            case "object" -> {
                JsonObjectSchema.Builder builder =
                        JsonObjectSchema.builder().description((String) map.get("description"));

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

                Object additionalProps = map.get("additionalProperties");
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
                JsonArraySchema.Builder builder =
                        JsonArraySchema.builder().description((String) map.get("description"));

                Object itemsObj = map.get("items");
                if (itemsObj instanceof Map) {
                    builder.items(fromMap((Map<String, Object>) itemsObj));
                } else if (itemsObj != null) {
                    throw new IllegalArgumentException(
                            "\"items\" must be a JSON object, but was: " + className(itemsObj));
                }

                yield builder.build();
            }
            default -> JsonRawSchema.from(Json.toJson(map));
        };
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
