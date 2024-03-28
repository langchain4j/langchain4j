package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;

import static dev.langchain4j.service.DefaultServiceOutputParser.DEFAULT;

/**
 * The ServiceOutputParser class provides methods for parsing and formatting service outputs.
 * It's a static version of {@see DefaultServiceOutputParser} for backwards compatibility
 * For more control over output parsing use directly {@see DefaultServiceOutputParser}
 */
public class ServiceOutputParser {
    public static Object parse(Response<AiMessage> response, Class<?> returnType) {
        return DEFAULT.parse(response, returnType);
    }

    public static String outputFormatInstructions(Class<?> returnType) {
        return DEFAULT.outputFormatInstructions(returnType);
    }
}