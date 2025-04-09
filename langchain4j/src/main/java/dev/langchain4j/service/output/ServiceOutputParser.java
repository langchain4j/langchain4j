package dev.langchain4j.service.output;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.TypeUtils;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.TypeUtils.getRawClass;
import static dev.langchain4j.service.TypeUtils.resolveFirstGenericParameterClass;
import static dev.langchain4j.service.TypeUtils.resolveFirstGenericParameterType;
import static dev.langchain4j.service.TypeUtils.typeHasRawClass;

public class ServiceOutputParser {

    private final OutputParserFactory outputParserFactory;

    public ServiceOutputParser() {
        this(new DefaultOutputParserFactory());
    }

    ServiceOutputParser(OutputParserFactory outputParserFactory) {
        this.outputParserFactory = ensureNotNull(outputParserFactory, "outputParserFactory");
    }

    public Object parse(Response<AiMessage> response, Type returnType) { // TODO Response -> ChatResponse?

        if (typeHasRawClass(returnType, Result.class)) {
            // In the case of returnType = Result<List<String>>, returnType will be set to List<String>
            returnType = resolveFirstGenericParameterType(returnType);
        }

        // In the case of returnType = List<String> these two would be set like:
        // rawClass = List.class
        // typeArgumentClass = String.class
        Class<?> rawClass = getRawClass(returnType);
        Class<?> typeArgumentClass = resolveFirstGenericParameterClass(returnType);

        if (rawClass == Response.class) { // TODO remove?
            return response;
        }

        AiMessage aiMessage = response.content();
        if (rawClass == AiMessage.class) {
            return aiMessage;
        }

        String text = aiMessage.text();
        if (rawClass == String.class) {
            return text;
        }

        OutputParser<?> outputParser = outputParserFactory.get(rawClass, typeArgumentClass);
        return outputParser.parse(text);
    }

    public Optional<JsonSchema> jsonSchema(Type returnType) {

        if (typeHasRawClass(returnType, Result.class)) {
            // In the case of returnType = Result<List<String>>, returnType will be set to List<String>
            returnType = resolveFirstGenericParameterType(returnType);
        }

        // In the case of returnType = List<String> these two would be set like:
        // rawClass = List.class
        // typeArgumentClass = String.class
        Class<?> rawClass = getRawClass(returnType);
        Class<?> typeArgumentClass = resolveFirstGenericParameterClass(returnType);

        if (schemaNotRequired(rawClass)) {
            return Optional.empty();
        }

        // TODO validate this earlier
        if (returnType == void.class) {
            throw illegalConfiguration("Return type of method '%s' cannot be void");
        }

        OutputParser<?> outputParser = outputParserFactory.get(rawClass, typeArgumentClass);
        return outputParser.jsonSchema();
    }

    public String outputFormatInstructions(Type returnType) {

        if (typeHasRawClass(returnType, Result.class)) {
            // In the case of returnType = Result<List<String>>, returnType will be set to List<String>
            returnType = resolveFirstGenericParameterType(returnType);
        }

        // In the case of returnType = List<String> these two would be set like:
        // rawClass = List.class
        // typeArgumentClass = String.class
        Class<?> rawClass = getRawClass(returnType);
        Class<?> typeArgumentClass = resolveFirstGenericParameterClass(returnType);

        if (schemaNotRequired(rawClass)) {
            return "";
        }

        // TODO validate this earlier
        if (returnType == void.class) {
            throw illegalConfiguration("Return type of method '%s' cannot be void");
        }

        OutputParser<?> outputParser = outputParserFactory.get(rawClass, typeArgumentClass);
        String formatInstructions = outputParser.formatInstructions();
        if (!formatInstructions.startsWith("\nYou must")) {
            formatInstructions = "\nYou must answer strictly in the following format: " + formatInstructions;
        }
        return formatInstructions;
    }

    private static boolean schemaNotRequired(Class<?> type) {
        return type == String.class
                || type == AiMessage.class
                || type == TokenStream.class
                || type == Response.class
                || type == Map.class;
    }
}
