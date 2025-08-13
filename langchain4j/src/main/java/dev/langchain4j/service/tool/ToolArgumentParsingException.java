package dev.langchain4j.service.tool;

import dev.langchain4j.exception.LangChain4jException;

/**
 * @since 1.4.0
 */
public class ToolArgumentParsingException extends LangChain4jException { // TODO name, need, location

    public ToolArgumentParsingException(Throwable cause) {
        super(cause);
    }
}
