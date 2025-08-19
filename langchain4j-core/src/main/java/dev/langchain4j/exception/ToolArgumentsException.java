package dev.langchain4j.exception;

/**
 * @since 1.4.0
 */
public class ToolArgumentsException extends LangChain4jException {

    private final Integer errorCode;

    public ToolArgumentsException(Throwable cause) {
        super(cause);
        this.errorCode = null;
    }

    public ToolArgumentsException(Throwable cause, Integer errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public Integer errorCode() {
        return errorCode;
    }
}
