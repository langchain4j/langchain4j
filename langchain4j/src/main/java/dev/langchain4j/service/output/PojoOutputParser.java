package dev.langchain4j.service.output;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.structured.Description;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonObjectOrReferenceSchemaFrom;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.output.ParsingUtils.outputParsingException;
import static java.lang.String.format;

@Internal
class PojoOutputParser<T> implements OutputParser<T> {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)\\{.*\\}|\\[.*\\]");

    private final Class<T> type;

    PojoOutputParser(Class<T> type) {
        this.type = type;
    }

    @Override
    public T parse(String text) {
        if (isNullOrBlank(text)) {
            throw outputParsingException(text, type);
        }

        try {
            return Json.fromJson(text, type);
        } catch (Exception ignored) {
            try {
                String jsonBlock = extractJsonBlock(text);
                return Json.fromJson(jsonBlock, type);
            } catch (Exception innerException) {
                throw outputParsingException(text, type.getName(), innerException);
            }
        }
    }

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name(type.getSimpleName())
                .rootElement(jsonObjectOrReferenceSchemaFrom(type, null, false, new LinkedHashMap<>(), true))
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        String jsonStructure = jsonStructure(type, new HashSet<>());
        validateJsonStructure(jsonStructure, type);
        return "\nYou must answer strictly in the following JSON format: " + jsonStructure;
    }

    private static String jsonStructure(Class<?> type, Set<Class<?>> visited) {
        StringBuilder jsonSchema = new StringBuilder();

        jsonSchema.append("{\n");
        for (Field field : type.getDeclaredFields()) {
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

        if (type instanceof ParameterizedType parameterizedType) {
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
        return switch (type.getTypeName()) {
            case "java.lang.String" -> "string";
            case "java.lang.Integer", "int" -> "integer";
            case "java.lang.Boolean", "boolean" -> "boolean";
            case "java.lang.Float", "float" -> "float";
            case "java.lang.Double", "double" -> "double";
            case "java.util.Date", "java.time.LocalDate" -> "date string (2023-12-31)";
            case "java.time.LocalTime" -> "time string (23:59:59)";
            case "java.time.LocalDateTime" -> "date-time string (2023-12-31T23:59:59)";
            default -> type.getTypeName();
        };
    }

    private String extractJsonBlock(String text) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return text;
    }

    private void validateJsonStructure(String jsonStructure, Type returnType) {
        if (jsonStructure.replaceAll("\\s", "").equals("{}")) {
            if (returnType.toString().contains("reactor.core.publisher.Flux")) {
                throw illegalConfiguration("Please import langchain4j-reactor module " +
                        "if you wish to use Flux<String> as a method return type");
            }
            throw illegalConfiguration("Illegal method return type: " + returnType);
        }
    }
}
