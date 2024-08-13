package dev.langchain4j.service.output;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.model.output.structured.json.*;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.TypeUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.internal.TypeUtils.*;
import static dev.langchain4j.service.TypeUtils.*;
import static java.lang.reflect.Modifier.isStatic;

@Experimental
public class JsonSchemas { // TODO name, refactor

    public static Optional<JsonSchema> from(Type returnType) {

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
            return Optional.empty();
        }

        // TODO validate this earlier
        if (returnType == void.class) {
            throw illegalConfiguration("Return type of method '%s' cannot be void");
        }

        Optional<OutputParser<?>> outputParser = new DefaultOutputParserFactory().get(rawClass, typeArgumentClass);
        if (outputParser.isPresent()) {
            return Optional.empty();
        }

        JsonSchema jsonSchema = JsonSchema.builder()
                .name(rawClass.getSimpleName())
                .schema(toJsonObjectSchema(rawClass, null))
                .build();

        return Optional.of(jsonSchema);
    }

    private static JsonObjectSchema toJsonObjectSchema(Class<?> type, String description) {

        Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        for (Field field : type.getDeclaredFields()) {
            String fieldName = field.getName();
            if (isStatic(field.getModifiers()) || fieldName.equals("__$hits$__") || fieldName.startsWith("this$")) {
                continue;
            }
            String fieldDescription = getDescription(field);
            JsonSchemaElement jsonSchemaElement = jsonSchema(field.getType(), field.getGenericType(), fieldDescription);
            properties.put(fieldName, jsonSchemaElement);
        }

        return JsonObjectSchema.builder()
                .description(Optional.ofNullable(description).orElse(getDescription(type)))
                .properties(properties)
                .required(new ArrayList<>(properties.keySet()))
                .additionalProperties(false)
                .build();
    }

    private static String getDescription(Field field) {
        return getDescription(field.getAnnotation(Description.class));
    }

    private static String getDescription(Class<?> type) {
        return getDescription(type.getAnnotation(Description.class));
    }

    private static String getDescription(Description description) {
        if (description == null) {
            return null;
        }
        return String.join(" ", description.value());
    }

    private static JsonSchemaElement jsonSchema(Class<?> clazz, Type type, String fieldDescription) {

        if (clazz == String.class) {
            return JsonStringSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonInteger(clazz)) {
            return JsonIntegerSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonNumber(clazz)) {
            return JsonNumberSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonBoolean(clazz)) {
            return JsonBooleanSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (clazz.isEnum()) {
            return JsonEnumSchema.builder()
                    .enumValues(clazz)
                    .description(Optional.ofNullable(fieldDescription).orElse(getDescription(clazz)))
                    .build();
        }

        if (clazz.isArray()) {
            return JsonArraySchema.builder()
                    .items(jsonSchema(clazz.getComponentType(), null, null))
                    .description(fieldDescription)
                    .build();
        }

        if (clazz.equals(List.class) || clazz.equals(Set.class)) {
            return JsonArraySchema.builder()
                    .items(jsonSchema(getActualType(type), null, null))
                    .description(fieldDescription)
                    .build();
        }

        return toJsonObjectSchema(clazz, fieldDescription);
    }

    private static Class<?> getActualType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                return (Class<?>) actualTypeArguments[0];
            }
        }
        return null;
    }
}
