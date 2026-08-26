package dev.langchain4j.exception;

/**
 * Thrown when LangChain4j cannot read or write JSON.
 *
 * <p>Which JSON library is doing the work is a detail of how the application is assembled - adding
 * {@code langchain4j-json-jackson3} swaps it - so the exception a caller sees must not change with
 * it. That library's own failure is kept as the {@linkplain #getCause() cause}.
 *
 * <p>Catch {@link JsonReadException} or {@link JsonWriteException} to tell the two apart: a read
 * failure means the input was not what was expected, which is often worth retrying or reporting,
 * while a write failure means the object being written cannot be represented, which is a bug.
 *
 * <p>The text that could not be read is deliberately not part of the message. It is untrusted input
 * that regularly carries credentials or user data, and exception messages end up in logs.
 */
public class JsonException extends LangChain4jException {

    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
