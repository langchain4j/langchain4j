package dev.langchain4j.service.tool;

import dev.langchain4j.exception.LangChain4jException;

/**
 * @since 1.4.0
 */
public class ToolExecutionException extends LangChain4jException { // TODO name, location (package, module)

    private final Integer errorCode;

    public ToolExecutionException(String message) {
        super(message);
        this.errorCode = null;
    }

    public ToolExecutionException(String message, Integer errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ToolExecutionException(Throwable cause) {
        super(cause);
        this.errorCode = null;
    }

    public Integer errorCode() {
        return errorCode;
    }
}
