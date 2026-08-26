package dev.langchain4j.exception;

/**
 * Thrown when JSON cannot be read: it is malformed, or it does not describe the expected type.
 *
 * <p>The input is usually not ours - a model's answer, a provider's response, a stored document - so
 * this is often recoverable: reprompt, retry, or report it to the user.
 */
public class JsonReadException extends JsonException {

    public JsonReadException(Throwable cause) {
        this("Failed to read JSON", cause);
    }

    public JsonReadException(String message, Throwable cause) {
        // The cause's own message says what went wrong; both Jackson versions redact the source
        // text from it, so this carries the reason without carrying the input.
        super(cause == null || cause.getMessage() == null ? message : message + ": " + cause.getMessage(), cause);
    }
}
