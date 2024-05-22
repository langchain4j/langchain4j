package dev.langchain4j.agent.tool;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * Utility methods for {@link ToolSpecification}s.
 */
public class ToolSpecifications {

    private ToolSpecifications() {
    }

    /**
     * Returns {@link ToolSpecification}s for all methods annotated with @{@link Tool} within the specified class.
     *
     * @param classWithTools the class.
     * @return the {@link ToolSpecification}s.
     */
    public static List<ToolSpecification> toolSpecificationsFrom(Class<?> classWithTools) {
        return stream(classWithTools.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(ToolSpecifications::toolSpecificationFrom)
                .collect(toList());
    }

    /**
     * Returns {@link ToolSpecification}s for all methods annotated with @{@link Tool}
     * within the class of the specified object.
     *
     * @param objectWithTools the object.
     * @return the {@link ToolSpecification}s.
     */
    public static List<ToolSpecification> toolSpecificationsFrom(Object objectWithTools) {
        return toolSpecificationsFrom(objectWithTools.getClass());
    }

    /**
     * Returns the {@link ToolSpecification} for the given method annotated with @{@link Tool}.
     *
     * @param method the method.
     * @return the {@link ToolSpecification}.
     */
    public static ToolSpecification toolSpecificationFrom(Method method) {
        Tool annotation = method.getAnnotation(Tool.class);

        String name = isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
        String description = String.join("\n", annotation.value()); // TODO provide null instead of "" ?

        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(name)
                .description(description);

        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(ToolMemoryId.class)) {
                continue;
            }
            builder.addParameter(parameter.getName(), toJsonSchemaProperties(parameter));
        }

        return builder.build();
    }

    /**
     * @param clazz the clazz.
     * @param type the type.
     * @param annotation the annotation.
     * @return the {@link JsonSchemaProperty}.
     */
    public static Iterable<JsonSchemaProperty> toJsonSchemaProperties(Class<?> clazz, Type type, P annotation) {
        JsonSchemaProperty description = annotation == null ? null : description(annotation.value());
        if (type == String.class) {
            return removeNulls(STRING, description);
        }

        if (isBoolean(clazz)) {
            return removeNulls(BOOLEAN, description);
        }

        if (isInteger(clazz)) {
            return removeNulls(INTEGER, description);
        }

        if (isNumber(clazz)) {
            return removeNulls(NUMBER, description);
        }

        if (clazz.isArray()) {
            return removeNulls(ARRAY, arrayTypeFrom(clazz.getComponentType()), description);
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            return removeNulls(ARRAY, arrayTypeFrom(type), description);
        }

        if (clazz.isEnum()) {
            return removeNulls(STRING, enums((Class<?>) type), description);
        }

        return removeNulls(OBJECT, properties(clazz), description);
    }

    /**
     * Convert a {@link Parameter} to a {@link JsonSchemaProperty}.
     *
     * @param parameter the parameter.
     * @return the {@link JsonSchemaProperty}.
     */
    static Iterable<JsonSchemaProperty> toJsonSchemaProperties(Parameter parameter) {
        return toJsonSchemaProperties(parameter.getType(), parameter.getParameterizedType(), parameter.getAnnotation(P.class));
    }

    private static JsonSchemaProperty arrayTypeFrom(Type type) {
        Class<?> clazz = null;
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                clazz = (Class<?>) actualTypeArguments[0];
            }
        }
        return arrayTypeFrom(clazz);
    }

    // TODO put constraints on min and max?
    private static boolean isNumber(Class<?> type) {
        return type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == BigDecimal.class;
    }

    private static boolean isInteger(Class<?> type) {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == BigInteger.class;
    }

    private static boolean isBoolean(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    private static JsonSchemaProperty arrayTypeFrom(Class<?> clazz) {
        if (clazz == String.class) {
            return items(JsonSchemaProperty.STRING);
        }
        if (isBoolean(clazz)) {
            return items(JsonSchemaProperty.BOOLEAN);
        }
        if (isInteger(clazz)) {
            return items(JsonSchemaProperty.INTEGER);
        }
        if (isNumber(clazz)) {
            return items(JsonSchemaProperty.NUMBER);
        }
        return items(JsonSchemaProperty.OBJECT, properties(clazz));
    }

    /**
     * Remove nulls from the given array.
     *
     * @param items the array
     * @return an iterable of the non-null items.
     */
    static Iterable<JsonSchemaProperty> removeNulls(JsonSchemaProperty... items) {
        return stream(items)
                .filter(Objects::nonNull)
                .collect(toList());
    }

    public static JsonSchemaProperty properties(Class<?> type) {
        if (null != type) {
            Map<String, Object> fieldMap = new HashMap<>();
            for (Field field : type.getDeclaredFields()) {
                Iterable<JsonSchemaProperty> properties = toJsonSchemaProperties(field.getType(), field.getGenericType(), field.getAnnotation(P.class));
                fieldMap.put(field.getName(), StreamSupport.stream(properties.spliterator(), false).collect(Collectors.toMap(JsonSchemaProperty::key, JsonSchemaProperty::value, (a, b) -> a)));
            }
            if (!fieldMap.isEmpty()) {
                return JsonSchemaProperty.property("properties", fieldMap);
            }
        }
        return null;
    }
}
