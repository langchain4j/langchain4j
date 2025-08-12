package dev.langchain4j.service.tool;

import dev.langchain4j.exception.LangChain4jException;

/**
 * @since 1.4.0
 */
public class ToolExecutionException extends LangChain4jException { // TODO name

    public ToolExecutionException(Throwable cause) {
        super(cause);
    }
}
