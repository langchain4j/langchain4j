package dev.langchain4j.exception;

/**
 * Indicates that something went wrong while executing the tool.
 *
 * @since 1.4.0
 */
public class ToolExecutionException extends LangChain4jException {

    private final Integer errorCode;

    public ToolExecutionException(String message) {
        this(message, null);
    }

    public ToolExecutionException(Throwable cause) {
        this(cause, null);
    }

    public ToolExecutionException(String message, Integer errorCode) {
        this(new RuntimeException(message), errorCode);
    }

    public ToolExecutionException(Throwable cause, Integer errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public Integer errorCode() {
        return errorCode;
    }
}
