package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.*;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;

/**
 * DefaultServiceOutputParser provides methods to parse service output and provide format instructions for different return types.
 */
public class DefaultServiceOutputParser {

    public static final DefaultServiceOutputParser DEFAULT = new DefaultServiceOutputParser();
    private final OutputParserResolver  outputParserResolver;

    public DefaultServiceOutputParser(final OutputParserResolver outputParserResolver) {
        this.outputParserResolver = outputParserResolver;
    }

    public DefaultServiceOutputParser() {
        this(new OutputParserResolver());
    }

    public <T> Object parse(Response<AiMessage> response, Class<T> returnType) {
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

        return outputParserResolver.resolve(returnType).parse(text);
    }

    public <T> String outputFormatInstructions(Class<T> returnType) {
        if (returnType == void.class) throw illegalConfiguration("Return type of method '%s' cannot be void");

        if (returnType == String.class
                || returnType == AiMessage.class
                || returnType == TokenStream.class
                || returnType == Response.class) {
            return "";
        }

        return outputParserResolver.resolve(returnType).formatInstructions();
    }


}
