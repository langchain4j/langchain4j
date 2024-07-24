package dev.langchain4j.service.output;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;

import java.lang.reflect.Type;

/**
 * A ServiceOutputParser handles conversion from expected entity to output format instructions
 * and parses the response.
 * 
 * @see DefaultServiceOutputParser
 */
public interface ServiceOutputParser {

    /**
     * Provides instructions for how to format output based the expected {@code returnType}.
     * 
     * @param returnType Expected return type
     * @return Formatting instruction that will be augmented to user message
     */
    String outputFormatInstructions(Type returnType);
    
    /**
     * Parses response from AI service and converts to an instance of {@code returnType}.
     * 
     * @param response Response from AI service
     * @param returnType Expected return type
     * @return Parsed instance
     */
    Object parse(Response<AiMessage> response, Type returnType);
}
