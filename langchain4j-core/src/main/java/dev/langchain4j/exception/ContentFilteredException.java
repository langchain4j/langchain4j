package dev.langchain4j.exception;

/**
 * Exception thrown when the LLM provider refuses to process a request due to content filtering
 * or violation of usage policies.
 * <p>
 * This typically indicates that the input was flagged as inappropriate, unsafe, or against
 * the provider’s content guidelines.
 *
 * @since 1.2.0
 */
public class ContentFilteredException extends InvalidRequestException {

    public ContentFilteredException(String message) {
        super(message);
    }

    public ContentFilteredException(Throwable cause) {
        super(cause);
    }

    public ContentFilteredException(String message, Throwable cause) {
        super(message, cause);
    }
}
