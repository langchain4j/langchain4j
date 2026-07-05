package dev.langchain4j.exception;

/**
 * Indicates a transient error that may succeed if the request is retried.
 * <p>
 * Examples include temporary network issues, upstream server errors (5xx),
 * and rate-limit rejections. Callers that implement a retry loop should
 * catch this exception (or one of its subclasses) and back off before
 * re-issuing the request.
 *
 * @see NonRetriableException
 * @see RateLimitException
 * @see InternalServerException
 * @see TimeoutException
 */
public class RetriableException extends LangChain4jException {
    public RetriableException(String message) {
        super(message);
    }

    public RetriableException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public RetriableException(String message, Throwable cause) {
        super(message, cause);
    }
}
