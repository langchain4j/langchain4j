package dev.langchain4j.service.output;

import static dev.langchain4j.service.TypeUtils.getRawClass;
import static dev.langchain4j.service.TypeUtils.resolveFirstGenericParameterClass;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.TokenStream;
import java.lang.reflect.Type;
import java.util.Optional;

public class JsonSchemas {

    public static Optional<JsonSchema> jsonSchemaFrom(Type returnType) {

        if (returnType == void.class || isExcluded(returnType)) {
            return Optional.empty();
        }

        OutputParser<?> outputParser = outputParserFor(returnType);
        return jsonSchemaFrom(outputParser);
    }

    private static OutputParser<?> outputParserFor(Type returnType) {
        Class<?> rawClass = getRawClass(returnType);
        Class<?> typeArgumentClass = resolveFirstGenericParameterClass(returnType);
        return new DefaultOutputParserFactory().get(rawClass, typeArgumentClass);
    }

    private static Optional<JsonSchema> jsonSchemaFrom(OutputParser<?> outputParser) {
        if (outputParser instanceof PojoOutputParser || outputParser instanceof PolymorphicOutputParser) {
            return outputParser.jsonSchema();
        }
        return Optional.empty();
    }

    private static boolean isExcluded(Type returnType) {
        return returnType == String.class
                || returnType == AiMessage.class
                || returnType == TokenStream.class
                || returnType == Response.class;
    }
}
