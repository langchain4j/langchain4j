package dev.langchain4j.exception;

/**
 * Indicates that something is wrong with the tool arguments.
 * For example, the JSON cannot be parsed, or an argument is of the wrong type.
 *
 * @since 1.4.0
 */
public class ToolArgumentsException extends LangChain4jException {

    private final Long errorCode;

    public ToolArgumentsException(String message) {
        this(message, (Integer) null);
    }

    public ToolArgumentsException(Throwable cause) {
        this(cause, null);
    }

    public ToolArgumentsException(String message, Long errorCode) {
        this(new RuntimeException(message), errorCode);
    }

    public ToolArgumentsException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public ToolArgumentsException(Throwable cause, Long errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public ToolArgumentsException(String message, Throwable cause, Long errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public Long errorCode() {
        return errorCode;
    }
}
