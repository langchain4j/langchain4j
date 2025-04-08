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

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.jsonObjectOrReferenceSchemaFrom;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
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

        if (returnType == String.class
                || returnType == AiMessage.class
                || returnType == TokenStream.class
                || returnType == Response.class) {
            return Optional.empty();
        }

        // TODO validate this earlier
        if (returnType == void.class) {
            throw illegalConfiguration("Return type of method '%s' cannot be void");
        }

        if (returnType == boolean.class || returnType == Boolean.class) {
            return booleanJsonSchema();
        }

        if (returnType == int.class || returnType == Integer.class) {
            return integerJsonSchema();
        }

        if (returnType == long.class || returnType == Long.class) {
            return longJsonSchema();
        }

        if (returnType == float.class || returnType == Float.class) {
            return floatJsonSchema();
        }

        if (returnType == double.class || returnType == Double.class) {
            return doubleJsonSchema();
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

    private static Optional<JsonSchema> booleanJsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("boolean")
                .rootElement(JsonObjectSchema.builder()
                        .addBooleanProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    private static Optional<JsonSchema> integerJsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("integer") // TODO add range? description?
                .rootElement(JsonObjectSchema.builder()
                        .addIntegerProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    private static Optional<JsonSchema> longJsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("long") // TODO add range? description?
                .rootElement(JsonObjectSchema.builder()
                        .addIntegerProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    private static Optional<JsonSchema> floatJsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("float") // TODO add range? description?
                .rootElement(JsonObjectSchema.builder()
                        .addNumberProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    private static Optional<JsonSchema> doubleJsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("double") // TODO add range? description?
                .rootElement(JsonObjectSchema.builder()
                        .addNumberProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
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
}
