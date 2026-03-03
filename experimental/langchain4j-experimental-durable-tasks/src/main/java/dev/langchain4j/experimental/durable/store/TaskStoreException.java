package dev.langchain4j.experimental.durable.store;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when a {@link TaskExecutionStore} operation fails.
 */
@Experimental
public class TaskStoreException extends LangChain4jException {

    public TaskStoreException(String message) {
        super(message);
    }

    public TaskStoreException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    public TaskStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
