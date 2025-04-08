package dev.langchain4j.service.output;

import dev.langchain4j.exception.LangChain4jException;

// TODO name
public class OutputParsingException extends LangChain4jException {

    public OutputParsingException(String message) {
        super(message);
    }

    public OutputParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
