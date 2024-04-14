package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.output.*;
import dev.langchain4j.model.output.structured.Description;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static java.lang.String.format;
import static java.util.Arrays.asList;

public class ServiceOutputParser {

    private static final Map<Class<?>, OutputParser<?>> OUTPUT_PARSERS = new HashMap<>();

    static {
        OUTPUT_PARSERS.put(boolean.class, new BooleanOutputParser());
        OUTPUT_PARSERS.put(Boolean.class, new BooleanOutputParser());

        OUTPUT_PARSERS.put(byte.class, new ByteOutputParser());
        OUTPUT_PARSERS.put(Byte.class, new ByteOutputParser());

        OUTPUT_PARSERS.put(short.class, new ShortOutputParser());
        OUTPUT_PARSERS.put(Short.class, new ShortOutputParser());

        OUTPUT_PARSERS.put(int.class, new IntOutputParser());
        OUTPUT_PARSERS.put(Integer.class, new IntOutputParser());

        OUTPUT_PARSERS.put(long.class, new LongOutputParser());
        OUTPUT_PARSERS.put(Long.class, new LongOutputParser());

        OUTPUT_PARSERS.put(BigInteger.class, new BigIntegerOutputParser());

        OUTPUT_PARSERS.put(float.class, new FloatOutputParser());
        OUTPUT_PARSERS.put(Float.class, new FloatOutputParser());

        OUTPUT_PARSERS.put(double.class, new DoubleOutputParser());
        OUTPUT_PARSERS.put(Double.class, new DoubleOutputParser());

        OUTPUT_PARSERS.put(BigDecimal.class, new BigDecimalOutputParser());

        OUTPUT_PARSERS.put(Date.class, new DateOutputParser());
        OUTPUT_PARSERS.put(LocalDate.class, new LocalDateOutputParser());
        OUTPUT_PARSERS.put(LocalTime.class, new LocalTimeOutputParser());
        OUTPUT_PARSERS.put(LocalDateTime.class, new LocalDateTimeOutputParser());
    }

    public static Object parse(Response<AiMessage> response, Class<?> returnType) {

        if (returnType == Response.class) {
            return response;
        }

        AiMessage aiMessage = response.content();
        if (returnType == AiMessage.class) {
            return aiMessage;
        }

        String text = aiMessage.text();
        if (returnType == String.class) {
            return text;
        }

        OutputParser<?> outputParser = OUTPUT_PARSERS.get(returnType);
        if (outputParser != null) {
            return outputParser.parse(text);
        }

        if (returnType == List.class) {
            return asList(text.split("\n"));
        }

        if (returnType == Set.class) {
            return new HashSet<>(asList(text.split("\n")));
        }

        return Json.fromJson(text, returnType);
    }

    public static String outputFormatInstructions(Class<?> returnType) {

        if (returnType == String.class
                || returnType == AiMessage.class
                || returnType == TokenStream.class
                || returnType == Response.class) {
            return "";
        }

        if (returnType == void.class) {
            throw illegalConfiguration("Return type of method '%s' cannot be void");
        }

        if (returnType.isEnum()) {
            String formatInstructions = new EnumOutputParser(returnType.asSubclass(Enum.class)).formatInstructions();
            return "\nYou must answer strictly in the following format: " + formatInstructions;
        }

        OutputParser<?> outputParser = OUTPUT_PARSERS.get(returnType);
        if (outputParser != null) {
            String formatInstructions = outputParser.formatInstructions();
            return "\nYou must answer strictly in the following format: " + formatInstructions;
        }

        if (returnType == List.class || returnType == Set.class) {
            return "\nYou must put every item on a separate line.";
        }

        return "\nYou must answer strictly in the following JSON format: " + jsonStructure(returnType, new HashSet<>());
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
        jsonSchema.delete(jsonSchema.lastIndexOf(","), jsonSchema.lastIndexOf(",")+1);
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
}
