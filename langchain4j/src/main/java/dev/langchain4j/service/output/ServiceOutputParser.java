package dev.langchain4j.service.output;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.TypeUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.TypeUtils.*;
import static java.lang.String.format;

public class ServiceOutputParser {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)\\{.*\\}|\\[.*\\]");

    private final OutputParserFactory outputParserFactory;

    public ServiceOutputParser() {
        this(new DefaultOutputParserFactory());
    }

    ServiceOutputParser(OutputParserFactory outputParserFactory) {
        this.outputParserFactory = ensureNotNull(outputParserFactory, "outputParserFactory");
    }

    public Object parse(Response<AiMessage> response, Type returnType) {

        if (typeHasRawClass(returnType, Result.class)) {
            returnType = resolveFirstGenericParameterClass(returnType);
        }

        // Explanation (which will make this a lot easier to understand):
        // In the case of List<String> these two would be set like:
        // rawClass: List.class
        // typeArgumentClass: String.class
        Class<?> rawReturnClass = getRawClass(returnType);
        Class<?> typeArgumentClass = TypeUtils.resolveFirstGenericParameterClass(returnType);

        if (rawReturnClass == Response.class) {
            return response;
        }

        AiMessage aiMessage = response.content();
        if (rawReturnClass == AiMessage.class) {
            return aiMessage;
        }

        String text = aiMessage.text();
        if (rawReturnClass == String.class) {
            return text;
        }

        Optional<OutputParser<?>> optionalOutputParser = outputParserFactory.get(rawReturnClass, typeArgumentClass);
        if (optionalOutputParser.isPresent()) {
            return optionalOutputParser.get().parse(text);
        }

        try {
            return Json.fromJson(text, rawReturnClass);
        } catch (Exception e) {
            String jsonBlock = extractJsonBlock(text);
            return Json.fromJson(jsonBlock, rawReturnClass);
        }
    }

    public String outputFormatInstructions(Type returnType) {

        if (typeHasRawClass(returnType, Result.class)) {
            returnType = resolveFirstGenericParameterClass(returnType);
        }

        // Explanation (which will make this a lot easier to understand):
        // In the case of List<String> these two would be set like:
        // rawClass: List.class
        // typeArgumentClass: String.class
        Class<?> rawClass = getRawClass(returnType);
        Class<?> typeArgumentClass = TypeUtils.resolveFirstGenericParameterClass(returnType);

        if (rawClass == String.class
                || rawClass == AiMessage.class
                || rawClass == TokenStream.class
                || rawClass == Response.class) {
            return "";
        }

        // TODO validate this earlier
        if (returnType == void.class) {
            throw illegalConfiguration("Return type of method '%s' cannot be void");
        }

        Optional<OutputParser<?>> outputParser = outputParserFactory.get(rawClass, typeArgumentClass);
        if (outputParser.isPresent()) {
            String formatInstructions = outputParser.get().formatInstructions();

            if (rawClass == List.class ||
                    rawClass == Set.class ||
                    rawClass.isEnum()) {
                // In these cases complete instruction is already
                // constructed by concrete output parsers.
                return formatInstructions;
            } else {
                return "\nYou must answer strictly in the following format: " + formatInstructions;
            }
        }

        return "\nYou must answer strictly in the following JSON format: " + jsonStructure((rawClass), new HashSet<>());
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

    private String extractJsonBlock(String text) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return text;
    }
}
