package dev.langchain4j.model.output;

import dev.langchain4j.internal.Json;
import dev.langchain4j.model.output.structured.Description;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static dev.langchain4j.internal.Utils.setOf;
import static java.lang.String.format;

/**
 * Parser for JSON output.
 * @param <T> the type of the object to parse.
 */
@RequiredArgsConstructor(staticName = "forClass")
public class JsonOutputParser<T> implements TextOutputParser<T> {
    private final Class<T> clazz;

    @Override
    public T parse(final String text) {
        return Json.fromJson(text, clazz);
    }

    @Override
    public String formatInstructions() {
        return jsonStructure(clazz, new HashSet<>());
    }

    @Override
    public String customFormatPrelude() {
        return "\nYou must answer strictly in the following JSON format: ";
    }

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(clazz);
    }

    private static String jsonStructure(Class<?> structured, Set<Class<?>> visited) {
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

        int trailingCommaIndex = jsonSchema.lastIndexOf(",");
        if (trailingCommaIndex > 0) {
            jsonSchema.delete(trailingCommaIndex, trailingCommaIndex + 1);
        }
        jsonSchema.append("}");
        return jsonSchema.toString();
    }

    private static String descriptionFor(Field field, Set<Class<?>> visited) {
        Description fieldDescription = field.getAnnotation(Description.class);
        if (fieldDescription == null) {
            return "type: " + typeOf(field, visited);
        }

        return String.join(" ", fieldDescription.value()) + "; type: " + typeOf(field, visited);
    }

    private static String typeOf(Field field, Set<Class<?>> visited) {
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

    private static String simpleNameOrJsonStructure(Class<?> structured, Set<Class<?>> visited) {
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

    private static String simpleTypeName(Type type) {
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


    public static ParserFactory factory() {
        return new Factory();
    }

    public static class Factory implements ParserFactory {
        @Override
        public Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider) {
            return Optional.of(JsonOutputParser.forClass(typeInformation.getRawType()));
        }
    }
}
