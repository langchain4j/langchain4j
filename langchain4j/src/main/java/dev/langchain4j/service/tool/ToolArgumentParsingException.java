package dev.langchain4j.service.tool;

import dev.langchain4j.exception.LangChain4jException;

/**
 * @since 1.4.0
 */
public class ToolArgumentParsingException extends LangChain4jException { // TODO name, location (package, module)

    private final Integer errorCode;

    public ToolArgumentParsingException(Throwable cause) {
        super(cause);
        this.errorCode = null;
    }

    public ToolArgumentParsingException(Throwable cause, Integer errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public Integer errorCode() {
        return errorCode;
    }
}
