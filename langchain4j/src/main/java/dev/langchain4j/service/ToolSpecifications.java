package dev.langchain4j.service;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;

class ToolSpecifications {

    static ToolSpecification toolSpecificationFrom(Method method) {
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name())
                .description(toolAnnotation.value()); // TODO @Description ?

        for (Parameter parameter : method.getParameters()) {
            builder.addParameter(parameter.getName(), toJsonSchemaProperties(parameter));
        }

        return builder.build();
    }

    private static JsonSchemaProperty[] toJsonSchemaProperties(Parameter parameter) {

        Class<?> type = parameter.getType();

        if (type == String.class) {
            return new JsonSchemaProperty[]{STRING};
        }

        if (type == boolean.class || type == Boolean.class) {
            return new JsonSchemaProperty[]{BOOLEAN};
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
            return new JsonSchemaProperty[]{NUMBER};
        }

        if (type.isArray()
                || type == List.class
                || type == Set.class) { // TODO something else?
            return new JsonSchemaProperty[]{ARRAY}; // TODO provide type of array?
        }

        if (type.isEnum()) {
            return new JsonSchemaProperty[]{STRING, enums((Object[]) type.getEnumConstants())};
        }

        return new JsonSchemaProperty[]{OBJECT}; // TODO provide internals
    }
}
