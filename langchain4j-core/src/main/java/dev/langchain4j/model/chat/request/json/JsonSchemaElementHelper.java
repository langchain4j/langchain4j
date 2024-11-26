package dev.langchain4j.model.chat.request.json;

import dev.langchain4j.model.output.structured.Description;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.internal.TypeUtils.isJsonBoolean;
import static dev.langchain4j.internal.TypeUtils.isJsonInteger;
import static dev.langchain4j.internal.TypeUtils.isJsonNumber;
import static dev.langchain4j.internal.TypeUtils.isJsonString;
import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Arrays.stream;

public class JsonSchemaElementHelper {

    public static JsonSchemaElement jsonSchemaElementFrom(Class<?> clazz) {
        return jsonSchemaElementFrom(clazz, clazz, null, new LinkedHashMap<>());
    }

    public static JsonSchemaElement jsonSchemaElementFrom(Class<?> clazz,
                                                          Type type,
                                                          String fieldDescription,
                                                          Map<Class<?>, VisitedClassMetadata> visited) {
        if (isJsonString(clazz)) {
            return JsonStringSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonInteger(clazz)) {
            return JsonIntegerSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonNumber(clazz)) {
            return JsonNumberSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonBoolean(clazz)) {
            return JsonBooleanSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (clazz.isEnum()) {
            return JsonEnumSchema.builder()
                    .enumValues(stream(clazz.getEnumConstants()).map(Object::toString).toList())
                    .description(Optional.ofNullable(fieldDescription).orElse(descriptionFrom(clazz)))
                    .build();
        }

        if (clazz.isArray()) {
            return JsonArraySchema.builder()
                    .items(jsonSchemaElementFrom(clazz.getComponentType(), null, null, visited))
                    .description(fieldDescription)
                    .build();
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            return JsonArraySchema.builder()
                    .items(jsonSchemaElementFrom(getActualType(type), null, null, visited))
                    .description(fieldDescription)
                    .build();
        }

        return jsonObjectOrReferenceSchemaFrom(clazz, fieldDescription, visited, false);
    }

    public static JsonSchemaElement jsonObjectOrReferenceSchemaFrom(Class<?> type,
                                                                    String description,
                                                                    Map<Class<?>, VisitedClassMetadata> visited,
                                                                    boolean setDefinitions) {
        if (visited.containsKey(type) && isCustomClass(type)) {
            VisitedClassMetadata visitedClassMetadata = visited.get(type);
            JsonSchemaElement jsonSchemaElement = visitedClassMetadata.jsonSchemaElement;
            if (jsonSchemaElement instanceof JsonReferenceSchema) {
                visitedClassMetadata.recursionDetected = true;
            }
            return jsonSchemaElement;
        }

        String reference = generateUUIDFrom(type.getName());
        JsonReferenceSchema jsonReferenceSchema = JsonReferenceSchema.builder()
                .reference(reference)
                .build();
        visited.put(type, new VisitedClassMetadata(jsonReferenceSchema, reference, false));

        Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        for (Field field : type.getDeclaredFields()) {
            String fieldName = field.getName();
            if (isStatic(field.getModifiers()) || fieldName.equals("__$hits$__") || fieldName.startsWith("this$")) {
                continue;
            }
            String fieldDescription = descriptionFrom(field);
            JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(
                    field.getType(),
                    field.getGenericType(),
                    fieldDescription,
                    visited
            );
            properties.put(fieldName, jsonSchemaElement);
        }

        JsonObjectSchema.Builder builder = JsonObjectSchema.builder()
                .description(Optional.ofNullable(description).orElse(descriptionFrom(type)))
                .properties(properties)
                .required(new ArrayList<>(properties.keySet()));

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

    private static String descriptionFrom(Field field) {
        return descriptionFrom(field.getAnnotation(Description.class));
    }

    private static String descriptionFrom(Class<?> type) {
        return descriptionFrom(type.getAnnotation(Description.class));
    }

    private static String descriptionFrom(Description description) {
        if (description == null) {
            return null;
        }
        return String.join(" ", description.value());
    }

    private static Class<?> getActualType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                return (Class<?>) actualTypeArguments[0];
            }
        }
        return null;
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
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        properties.forEach((property, value) -> map.put(property, toMap(value)));
        return map;
    }

    public static Map<String, Object> toMap(JsonSchemaElement jsonSchemaElement) {
        if (jsonSchemaElement instanceof JsonObjectSchema jsonObjectSchema) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "object");
            if (jsonObjectSchema.description() != null) {
                properties.put("description", jsonObjectSchema.description());
            }
            properties.put("properties", toMap(jsonObjectSchema.properties()));
            if (jsonObjectSchema.required() != null) {
                properties.put("required", jsonObjectSchema.required());
            }
            return properties;
        } else if (jsonSchemaElement instanceof JsonArraySchema jsonArraySchema) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "array");
            if (jsonArraySchema.description() != null) {
                properties.put("description", jsonArraySchema.description());
            }
            properties.put("items", toMap(jsonArraySchema.items()));
            return properties;
        } else if (jsonSchemaElement instanceof JsonEnumSchema jsonEnumSchema) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "string");
            if (jsonEnumSchema.description() != null) {
                properties.put("description", jsonEnumSchema.description());
            }
            properties.put("enum", jsonEnumSchema.enumValues());
            return properties;
        } else if (jsonSchemaElement instanceof JsonStringSchema jsonStringSchema) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "string");
            if (jsonStringSchema.description() != null) {
                properties.put("description", jsonStringSchema.description());
            }
            return properties;
        } else if (jsonSchemaElement instanceof JsonIntegerSchema jsonIntegerSchema) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "integer");
            if (jsonIntegerSchema.description() != null) {
                properties.put("description", jsonIntegerSchema.description());
            }
            return properties;
        } else if (jsonSchemaElement instanceof JsonNumberSchema jsonNumberSchema) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "number");
            if (jsonNumberSchema.description() != null) {
                properties.put("description", jsonNumberSchema.description());
            }
            return properties;
        } else if (jsonSchemaElement instanceof JsonBooleanSchema jsonBooleanSchema) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "boolean");
            if (jsonBooleanSchema.description() != null) {
                properties.put("description", jsonBooleanSchema.description());
            }
            return properties;
        } else {
            throw new IllegalArgumentException("Unknown type: " + jsonSchemaElement.getClass());
        }
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
