package dev.langchain4j.agent.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class ToolSpecifications {

    public static List<ToolSpecification> toolSpecificationsFrom(Object objectWithTools) {
        return stream(objectWithTools.getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(ToolSpecifications::toolSpecificationFrom)
                .collect(toList());
    }

    public static ToolSpecification toolSpecificationFrom(Method method) {
        Tool annotation = method.getAnnotation(Tool.class);

        String name = isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
        String description = String.join("\n", annotation.value());

        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(name)
                .description(description);

        for (Parameter parameter : method.getParameters()) {
            // ignore memory id
            if (parameter.isAnnotationPresent(ToolMemoryId.class)) {
                continue;
            }

            builder.addParameter(parameter.getName(), toJsonSchemaProperties(parameter));
        }

        return builder.build();
    }

    private static Iterable<JsonSchemaProperty> toJsonSchemaProperties(Parameter parameter) {

        Class<?> type = parameter.getType();

        P annotation = parameter.getAnnotation(P.class);
        JsonSchemaProperty description = annotation == null ? null : description(annotation.value());

        if (type == String.class) {
            return removeNulls(STRING, description);
        }

        if (type == boolean.class || type == Boolean.class) {
            return removeNulls(BOOLEAN, description);
        }

        if (type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == BigInteger.class) {
            return removeNulls(INTEGER, description);
        }

        // TODO put constraints on min and max?
        if (type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == BigDecimal.class) {
            return removeNulls(NUMBER, description);
        }

        if (type.isArray()
                || type == List.class
                || type == Set.class) { // TODO something else?
            return removeNulls(ARRAY, description); // TODO provide type of array?
        }

        if (type.isEnum()) {
            return removeNulls(STRING, enums((Object[]) type.getEnumConstants()), description);
        }

        return removeNulls(OBJECT, description); // TODO provide internals
    }

    private static Iterable<JsonSchemaProperty> removeNulls(JsonSchemaProperty... properties) {
        return stream(properties)
                .filter(Objects::nonNull)
                .collect(toList());
    }
}
