package dev.langchain4j.exception;

/**
 * Thrown when an object cannot be written as JSON.
 *
 * <p>Unlike {@link JsonReadException}, the fault is on this side of the wire: the object graph has
 * no JSON representation. Retrying does not help.
 */
public class JsonWriteException extends JsonException {

    public JsonWriteException(Throwable cause) {
        this("Failed to write JSON", cause);
    }

    public JsonWriteException(String message, Throwable cause) {
        // The cause's own message says what went wrong; both Jackson versions redact the source
        // text from it, so this carries the reason without carrying the input.
        super(cause == null || cause.getMessage() == null ? message : message + ": " + cause.getMessage(), cause);
    }
}
