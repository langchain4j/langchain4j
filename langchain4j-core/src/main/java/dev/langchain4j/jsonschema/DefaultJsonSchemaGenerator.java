package dev.langchain4j.jsonschema;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.exception.JsonSchemaGenerationException;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * This JSON Schema generator converts Java Built-in types to {@link JsonSchema} recursively.
 *
 * <p>The Java Built-in types include: {@link Boolean}, {@link Integer}, {@link Double}, {@link
 * String}, {@link Enum}, {@link List}, {@link Set}, {@link Collection}, {@link Map}, and {@link
 * Object}.
 */
public class DefaultJsonSchemaGenerator implements JsonSchemaService.JsonSchemaGenerator {

    /**
     * Convert a {@link Type} to a list of {@link JsonSchemaProperty}s.
     *
     * @param type the type to convert.
     * @return the {@link JsonSchemaProperty}s.
     */
    private static List<JsonSchemaProperty> toJsonSchemaProperties(Type type)
            throws JsonSchemaGenerationException {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return toJsonSchemaProperties((Class<?>) parameterizedType.getRawType(), () -> type);
        }
        return toJsonSchemaProperties((Class<?>) type, () -> type);
    }

    /**
     * Convert a {@link Class} to a {@link JsonSchemaProperty}.
     *
     * @param clazz the class.
     * @param resolveGenericType a supplier to resolve the generic type.
     * @return the {@link JsonSchemaProperty}.
     */
    private static List<JsonSchemaProperty> toJsonSchemaProperties(
            Class<?> clazz, Supplier<Type> resolveGenericType)
            throws JsonSchemaGenerationException {
        switch (JsonSchemaUtil.getJsonSchemaElementType(clazz)) {
            case BOOLEAN:
                return removeNulls(BOOLEAN);
            case INTEGER:
                return removeNulls(INTEGER);
            case NUMBER:
                return removeNulls(NUMBER);
            case STRING:
                return removeNulls(STRING);
            case ENUM:
                return removeNulls(STRING, enums(clazz));
            case ARRAY:
                return removeNulls(ARRAY, toJsonSchemaArrayItems(clazz, resolveGenericType));
            case OBJECT:
            default:
                return removeNulls(
                        OBJECT, toJsonSchemaObjectAdditionalProperties(clazz, resolveGenericType));
        }
    }

    /**
     * Build a {@link JsonSchemaProperty} for the items' type of the given array {@code clazz}.
     *
     * @param clazz the array class.
     * @param resolveGenericType a supplier to resolve the generic type.
     * @return the {@link JsonSchemaProperty} for the items' type.
     */
    private static JsonSchemaProperty toJsonSchemaArrayItems(
            Class<?> clazz, Supplier<Type> resolveGenericType)
            throws JsonSchemaGenerationException {
        if (clazz.isArray()) {
            Class<?> itemType = clazz.getComponentType();
            return items(toJsonSchemaProperties(itemType));
        }

        // Collection like List, Set, etc.
        Type genericType = resolveGenericType.get();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            if (parameterizedType.getActualTypeArguments().length == 1) {
                Type itemType = parameterizedType.getActualTypeArguments()[0];
                return items(toJsonSchemaProperties(itemType));
            }
        }

        // fallback to ignore the item definition as we can't determine its type
        return items(OBJECT);
    }

    /**
     * Build a {@link JsonSchemaProperty} for the value's type of the given object {@code clazz}.
     *
     * @param clazz the object class.
     * @param resolveGenericType a supplier to resolve the generic type.
     * @return the {@link JsonSchemaProperty} for the value's type.
     */
    private static JsonSchemaProperty toJsonSchemaObjectAdditionalProperties(
            Class<?> clazz, Supplier<Type> resolveGenericType)
            throws JsonSchemaGenerationException {
        if (Object.class == clazz || Void.class == clazz) {
            return null;
        }
        if (!Map.class.isAssignableFrom(clazz)) {
            throw new JsonSchemaGenerationException(
                    String.format(
                            "Unsupported Custom-type tool parameter: %s, "
                                    + "please use Map<String, ?> or add `langchain4j-jsonschema-service-*' dependency",
                            clazz));
        }

        Type genericType = resolveGenericType.get();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            if (parameterizedType.getActualTypeArguments().length == 2) {
                Class<?> keyType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                if (keyType != String.class) {
                    throw new JsonSchemaGenerationException(
                            String.format(
                                    "%s's key type must be String, but was: %s", clazz, keyType));
                }

                Type valueType = parameterizedType.getActualTypeArguments()[1];
                return additionalProperties(toJsonSchemaProperties(valueType));
            }
        }

        // fallback to ignore the additional properties definition as we can't determine its type
        return null;
    }

    /**
     * Remove nulls from the given array.
     *
     * @param items the array
     * @return an iterable of the non-null items.
     */
    static List<JsonSchemaProperty> removeNulls(JsonSchemaProperty... items) {
        return stream(items).filter(Objects::nonNull).collect(toList());
    }

    /**
     * Generate a JSON Schema from the given type.
     *
     * @param type the type to generate the schema from.
     * @return the generated schema.
     */
    @Override
    public JsonSchema generate(Type type) throws JsonSchemaGenerationException {
        return new JsonSchema(toJsonSchemaProperties(type), type);
    }
}
