package dev.langchain4j.service.output;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.TypeUtils;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.jsonObjectOrReferenceSchemaFrom;
import static dev.langchain4j.service.TypeUtils.getRawClass;
import static dev.langchain4j.service.TypeUtils.resolveFirstGenericParameterClass;
import static dev.langchain4j.service.TypeUtils.typeHasRawClass;
import static java.util.Arrays.stream;

@Experimental
public class JsonSchemas {

    public static Optional<JsonSchema> jsonSchemaFrom(Type returnType) {

        if (typeHasRawClass(returnType, Result.class)) {
            returnType = resolveFirstGenericParameterClass(returnType);
        }

        // TODO validate this earlier
        if (returnType == void.class) {
            throw illegalConfiguration("Return type of method '%s' cannot be void");
        }

        // TODO float, long, biginteger, bigdecimal, etc

        if (returnType == boolean.class || returnType == Boolean.class) {
            return getBooleanJsonSchema();
        }

        if (returnType == int.class || returnType == Integer.class) {
            return getIntegerJsonSchema();
        }

        if (returnType == double.class || returnType == Double.class) {
            return getDoubleJsonSchema();
        }

        if (!isPojo(returnType) && !isEnum(returnType) && !isListOfStrings(returnType) && !isSetOfStrings(returnType)) {
            return Optional.empty();
        }

        if (typeHasRawClass(returnType, List.class) || typeHasRawClass(returnType, Set.class)) {
            Class<?> actualType = resolveFirstGenericParameterClass(returnType);
            if (actualType != null && actualType.isEnum()) {
                return Optional.of(arraySchemaFrom(returnType, actualType, enumSchemaFrom(actualType)));
            } else if (actualType == String.class) {
                return Optional.of(arraySchemaFrom(returnType, actualType, new JsonStringSchema()));
            } else {
                return Optional.of(arraySchemaFrom(returnType, actualType, objectSchemaFrom(actualType)));
            }
        } else {
            Class<?> returnTypeClass = (Class<?>) returnType;
            if (returnTypeClass.isEnum()) {
                JsonSchema jsonSchema = JsonSchema.builder()
                        .name(returnTypeClass.getSimpleName())
                        .rootElement(JsonObjectSchema.builder()
                                .addProperty("value", enumSchemaFrom(returnTypeClass))
                                .required("value")
                                .build())
                        .build();
                return Optional.of(jsonSchema);
            } else {
                JsonSchema jsonSchema = JsonSchema.builder()
                        .name(returnTypeClass.getSimpleName())
                        .rootElement(objectSchemaFrom(returnTypeClass))
                        .build();
                return Optional.of(jsonSchema);
            }
        }
    }

    private static Optional<JsonSchema> getBooleanJsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("boolean")
                .rootElement(JsonObjectSchema.builder()
                        .addBooleanProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    private static Optional<JsonSchema> getIntegerJsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("integer")
                .rootElement(JsonObjectSchema.builder()
                        .addIntegerProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    private static Optional<JsonSchema> getDoubleJsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("double")
                .rootElement(JsonObjectSchema.builder()
                        .addNumberProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    private static boolean isListOfStrings(Type returnType) {
        return isCollectionOfStrings(returnType, List.class);
    }

    private static boolean isSetOfStrings(Type returnType) {
        return isCollectionOfStrings(returnType, Set.class);
    }

    private static boolean isCollectionOfStrings(Type returnType, Class<?> clazz) {
        Class<?> rawClass = getRawClass(returnType);
        Class<?> typeArgumentClass = TypeUtils.resolveFirstGenericParameterClass(returnType);
        return clazz.isAssignableFrom(rawClass) && String.class.equals(typeArgumentClass);
    }

    private static JsonSchemaElement objectSchemaFrom(Class<?> actualType) {
        return jsonObjectOrReferenceSchemaFrom(actualType, null, false, new LinkedHashMap<>(), true);
    }

    private static JsonEnumSchema enumSchemaFrom(Class<?> actualType) {
        return JsonEnumSchema.builder()
                .enumValues(stream(actualType.getEnumConstants()).map(Object::toString).toList())
                .build();
    }

    private static JsonSchema arraySchemaFrom(Type returnType, Class<?> actualType, JsonSchemaElement items) {
        return JsonSchema.builder()
                .name(getRawClass(returnType).getSimpleName() + "_of_" + actualType.getSimpleName())
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("items", JsonArraySchema.builder()
                                .items(items)
                                .build())
                        .required("items")
                        .build())
                .build();
    }

    static boolean isEnum(Type returnType) {
        if (returnType instanceof Class<?> && ((Class<?>) returnType).isEnum()) {
            return true;
        }

        Class<?> typeArgumentClass = TypeUtils.resolveFirstGenericParameterClass(returnType);
        return typeArgumentClass != null && typeArgumentClass.isEnum();
    }

    private static boolean isPojo(Type returnType) {

        if (returnType == String.class
                || returnType == AiMessage.class
                || returnType == TokenStream.class
                || returnType == Response.class) {
            return false;
        }

        // Explanation (which will make this a lot easier to understand):
        // In the case of List<String> these two would be set like:
        // rawClass: List.class
        // typeArgumentClass: String.class
        Class<?> rawClass = getRawClass(returnType);
        Class<?> typeArgumentClass = TypeUtils.resolveFirstGenericParameterClass(returnType);

        Optional<OutputParser<?>> outputParser = new DefaultOutputParserFactory().get(rawClass, typeArgumentClass);
        return outputParser.isEmpty();
    }
}
