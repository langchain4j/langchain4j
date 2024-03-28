package dev.langchain4j.model.output;

import dev.langchain4j.internal.Json;
import dev.langchain4j.model.output.structured.Description;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;

public class JsonOutputParser<T> implements OutputParser<T> {

    private final Class<T> type;

    public JsonOutputParser(Class<T> type) {
        this.type = type;
    }

    @Override
    public T parse(String string) {
        return Json.fromJson(string, type);
    }

    @Override
    public String formatInstructions() {
        return "\nYou must answer strictly in the following JSON format: " + jsonStructure(type, new HashSet<>());
    }

    private String jsonStructure(Class<?> structured, Set<Class<?>> visited) {
        StringBuilder jsonSchema = new StringBuilder();
        jsonSchema.append("{\n");
        for (Field field : structured.getDeclaredFields()) {
            String name = field.getName();
            if (name.equals("__$hits$__") || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                // Skip coverage instrumentation field.
                continue;
            }
            jsonSchema.append(format("\"%s\": (%s),\n", name, descriptionFor(field, visited)));
        }
        jsonSchema.append("}");
        return jsonSchema.toString();
    }

    private String descriptionFor(Field field, Set<Class<?>> visited) {
        Description fieldDescription = field.getAnnotation(Description.class);
        if (fieldDescription == null) {
            return "type: " + typeOf(field, visited);
        }

        return String.join(" ", fieldDescription.value()) + "; type: " + typeOf(field, visited);
    }

    private String typeOf(Field field, Set<Class<?>> visited) {
        Type type = field.getGenericType();

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] typeArguments = parameterizedType.getActualTypeArguments();

            if (parameterizedType.getRawType().equals(List.class)
                    || parameterizedType.getRawType().equals(Set.class)) {
                return format("array of %s", simpleNameOrJsonStructure((Class<?>) typeArguments[0], visited));
            }
        } else if (field.getType().isArray()) {
            return format("array of %s", simpleNameOrJsonStructure(field.getType().getComponentType(), visited));
        } else if (((Class<?>) type).isEnum()) {
            return "enum, must be one of " + Arrays.toString(((Class<?>) type).getEnumConstants());
        }

        return simpleNameOrJsonStructure(field.getType(), visited);
    }

    private String simpleNameOrJsonStructure(Class<?> structured, Set<Class<?>> visited) {
        String simpleTypeName = simpleTypeName(structured);
        if (structured.getPackage() == null
                || structured.getPackage().getName().startsWith("java.")
                || visited.contains(structured)) {
            return simpleTypeName;
        } else {
            visited.add(structured);
            return simpleTypeName + ": " + jsonStructure(structured, visited);
        }
    }

    private String simpleTypeName(Type type) {
        switch (type.getTypeName()) {
            case "java.lang.String":
                return "string";
            case "java.lang.Integer":
            case "int":
                return "integer";
            case "java.lang.Boolean":
            case "boolean":
                return "boolean";
            case "java.lang.Float":
            case "float":
                return "float";
            case "java.lang.Double":
            case "double":
                return "double";
            case "java.util.Date":
            case "java.time.LocalDate":
                return "date string (2023-12-31)";
            case "java.time.LocalTime":
                return "time string (23:59:59)";
            case "java.time.LocalDateTime":
                return "date-time string (2023-12-31T23:59:59)";
            default:
                return type.getTypeName();
        }
    }
}
