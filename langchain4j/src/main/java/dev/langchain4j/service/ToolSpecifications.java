package dev.langchain4j.service;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

class ToolSpecifications {

    static ToolSpecification toolSpecificationFrom(Method method) {
        Tool annotation = method.getAnnotation(Tool.class);

        String name = isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
        String description = String.join("\n", annotation.value());

        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(name)
                .description(description);

        for (Parameter parameter : method.getParameters()) {
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

        // TODO put constraints on min and max?
        if (type == byte.class
                || type == Byte.class
                || type == short.class
                || type == Short.class
                || type == int.class
                || type == Integer.class
                || type == long.class
                || type == Long.class
                || type == float.class
                || type == Float.class
                || type == double.class
                || type == Double.class // TODO bigdecimal, etc
        ) {
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
