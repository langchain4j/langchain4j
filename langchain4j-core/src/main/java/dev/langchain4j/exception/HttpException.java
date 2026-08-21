package dev.langchain4j.exception;

/**
 * Represents an HTTP-level error returned by a model provider.
 * <p>
 * Carries the raw HTTP status code alongside the error message so that
 * higher-level error mappers (such as {@code ExceptionMapper}) can translate
 * specific codes into more descriptive exceptions (e.g. 429 →
 * {@link RateLimitException}, 401 → {@link AuthenticationException}).
 * <p>
 * This class is typically not thrown directly by user-facing APIs; it is an
 * intermediate representation that the HTTP client layer produces before the
 * exception mapper converts it to a more specific type.
 *
 * @see dev.langchain4j.internal.ExceptionMapper
 */
public class HttpException extends LangChain4jException {

    private final int statusCode;

    public HttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
