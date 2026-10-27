package dev.langchain4j.service.output;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Optional;

import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonObjectOrReferenceSchemaFrom;
import static dev.langchain4j.service.TypeUtils.getRawClass;
import static dev.langchain4j.service.TypeUtils.resolveFirstGenericParameterClass;
import static dev.langchain4j.service.output.ServiceOutputParser.isReturnedAsIs;

public class JsonSchemas {

    /**
     * Generates a {@link JsonSchema} for the given POJO type, for example to configure a {@link ResponseFormat}.
     *
     * @param returnType the type to generate the JSON schema for
     * @return the JSON schema, or an empty {@link Optional} if the type is not a POJO
     * (e.g. {@code String}, {@code Integer}, an enum, a {@code List}, or a LangChain4j type
     * such as {@code AiMessage} or {@code ChatResponse})
     */
    public static Optional<JsonSchema> jsonSchemaFrom(Type returnType) {

        if (!isPojo(returnType) || returnType == void.class) {
            return Optional.empty();
        }

        Class<?> rawClass = getRawClass(returnType);

        JsonSchema jsonSchema = JsonSchema.builder()
                .name(rawClass.getSimpleName())
                .rootElement(jsonObjectOrReferenceSchemaFrom(rawClass, null, false, new LinkedHashMap<>(), true))
                .build();

        return Optional.of(jsonSchema);
    }

    private static boolean isPojo(Type returnType) {

        // Explanation (which will make this a lot easier to understand):
        // In the case of List<String> these two would be set like:
        // rawClass: List.class
        // typeArgumentClass: String.class
        Class<?> rawClass = getRawClass(returnType);
        Class<?> typeArgumentClass = resolveFirstGenericParameterClass(returnType);

        if (rawClass == String.class || isReturnedAsIs(rawClass)) {
            return false;
        }

        OutputParser<?> outputParser = new DefaultOutputParserFactory().get(rawClass, typeArgumentClass);
        return outputParser instanceof PojoOutputParser;
    }
}
