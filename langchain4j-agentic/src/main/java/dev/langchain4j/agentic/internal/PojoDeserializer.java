package dev.langchain4j.agentic.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;

/**
 * Utility for deserializing POJO objects from JSON/Map representations.
 * Uses an ObjectMapper configured with default typing to handle polymorphic types.
 */
public class PojoDeserializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PojoDeserializer() {}

    /**
     * Deserializes a value to the specified target type.
     * Handles Map (from JSON object) and String (JSON string) representations.
     *
     * @param value the value to deserialize (Map, String, or already-typed object)
     * @param targetType the target type to deserialize to
     * @return the deserialized object, or the original value if conversion is not possible
     */
    public static Object deserialize(Object value, Type targetType) {
        if (value == null) {
            return null;
        }

        Class<?> targetClass = rawType(targetType);

        // If already the correct type, return as-is
        if (targetClass.isInstance(value)) {
            return value;
        }

        // Handle Map (from JSON object)
        if (value instanceof java.util.Map<?, ?> map) {
            try {
                // Convert Map to JSON string then to target type
                String json = MAPPER.writeValueAsString(map);
                return MAPPER.readValue(json, targetClass);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(
                        "Failed to deserialize Map to " + targetClass.getName() + ": " + e.getMessage(), e);
            }
        }

        // Handle String (JSON string)
        if (value instanceof String s) {
            try {
                return MAPPER.readValue(s, targetClass);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(
                        "Failed to deserialize String to " + targetClass.getName() + ": " + e.getMessage(), e);
            }
        }

        // Cannot convert
        return value;
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof java.lang.reflect.ParameterizedType parameterizedType) {
            Class<?> clazz = (Class<?>) parameterizedType.getRawType();
            if (clazz == dev.langchain4j.agentic.scope.ResultWithAgenticScope.class) {
                return rawType(parameterizedType.getActualTypeArguments()[0]);
            }
            return clazz;
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}
