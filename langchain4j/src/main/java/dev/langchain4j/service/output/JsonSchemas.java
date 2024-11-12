package dev.langchain4j.service.output;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.TypeUtils;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
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

        if (!isPojo(returnType) && !isEnum(returnType)) {
            return Optional.empty();
        }

        Class<?> rawClass = getRawClass(returnType);

        JsonSchema jsonSchema;

        if (typeHasRawClass(returnType, List.class) || typeHasRawClass(returnType, Set.class)) {
            Class<?> actualType = resolveFirstGenericParameterClass(returnType);
            if (actualType != null && actualType.isEnum()) {
                jsonSchema = getJsonSchema(returnType, actualType,
                    JsonEnumSchema.builder()
                        .enumValues(stream(actualType.getEnumConstants()).map(Object::toString).toList())
                        .build());
            } else {
                jsonSchema = getJsonSchema(returnType, actualType,
                    jsonObjectOrReferenceSchemaFrom(actualType, null, new LinkedHashMap<>(), true));
            }
        } else {
            Class<?> returnTypeClass = (Class<?>) returnType;
            if (returnTypeClass.isEnum()) {
                List<String> enumValues = stream(returnTypeClass.getEnumConstants()).map(Object::toString).toList();
                jsonSchema = JsonSchema.builder()
                    .name(returnTypeClass.getSimpleName())
                    .rootElement(JsonObjectSchema.builder()
                        .addEnumProperty(returnTypeClass.getSimpleName(), enumValues)
                        .build())
                    .build();
            } else {
                jsonSchema = JsonSchema.builder()
                    .name(rawClass.getSimpleName())
                    .rootElement(jsonObjectOrReferenceSchemaFrom(rawClass, null, new LinkedHashMap<>(), true))
                    .build();
            }
        }

        return Optional.of(jsonSchema);
    }

    private static JsonSchema getJsonSchema(Type returnType, Class<?> rawClass, JsonSchemaElement items) {
        String collectionName = getRawClass(returnType).getSimpleName();

        return JsonSchema.builder()
            .name(collectionName + "_of_" + rawClass.getSimpleName())
            .rootElement(JsonObjectSchema.builder()
                .addProperty("items", JsonArraySchema.builder()
                    .items(items)
                    .build())
                .required("items")
                .build())
            .build();
    }

    private static boolean isEnum(Type returnType) {
        Class<?> typeArgumentClass = TypeUtils.resolveFirstGenericParameterClass(returnType);
        return typeArgumentClass != null && typeArgumentClass.isEnum() || (((Class<?>) returnType).isEnum());
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
        if (outputParser.isPresent()) {
            return false;
        }

        return true;
    }
}
