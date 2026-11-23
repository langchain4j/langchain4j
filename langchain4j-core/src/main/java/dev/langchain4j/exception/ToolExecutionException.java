package dev.langchain4j.exception;

/**
 * Indicates that something went wrong while executing the tool.
 *
 * @since 1.4.0
 */
public class ToolExecutionException extends LangChain4jException {

    private final Long errorCode;

    public ToolExecutionException(String message) {
        this(message, (Integer) null);
    }

    public ToolExecutionException(Throwable cause) {
        this(cause, null);
    }

    public ToolExecutionException(String message, Long errorCode) {
        this(new RuntimeException(message), errorCode);
    }

    public ToolExecutionException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public ToolExecutionException(Throwable cause, Long errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public ToolExecutionException(String message, Throwable cause, Long errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public Long errorCode() {
        return errorCode;
    }
}
