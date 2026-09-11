package dev.langchain4j.exception;

/**
 * Thrown when LangChain4j cannot read or write JSON.
 *
 * <p>Which JSON library is doing the work is a detail of how the application is assembled, so the
 * exception a caller sees should not depend on it. That library's own failure is kept as the
 * {@linkplain #getCause() cause}.
 *
 * <p><b>Not yet reported everywhere.</b> The codecs in {@code langchain4j-jackson3} report
 * failures as these types. The Jackson 2 codecs, which are what LangChain4j uses by default, still
 * wrap Jackson's own exception in a plain {@link RuntimeException} as they always have, so that
 * code written against them keeps working. They will move to these types in the next major
 * version; until then, catching {@link RuntimeException} is what covers both.
 *
 * <p>Catch {@link JsonReadException} or {@link JsonWriteException} to tell the two apart: a read
 * failure means the input was not what was expected, which is often worth retrying or reporting,
 * while a write failure means the object being written cannot be represented, which is a bug.
 *
 * <p>The document being read is deliberately not part of the message: it is untrusted input that
 * regularly carries credentials or user data, and exception messages end up in logs. What the
 * message does carry is the underlying library's reason for failing, and that reason can quote the
 * fragment it tripped on - the value that would not convert, or the token that would not parse. So
 * the message is bounded, not sanitised.
 */
public class JsonException extends LangChain4jException {

    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
