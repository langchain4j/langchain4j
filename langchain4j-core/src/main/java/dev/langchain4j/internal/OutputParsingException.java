package dev.langchain4j.internal;

import dev.langchain4j.exception.LangChain4jException;

public class OutputParsingException extends LangChain4jException {

    public OutputParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
