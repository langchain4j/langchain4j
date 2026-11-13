package dev.langchain4j.exception;

/**
 * Thrown when JSON that carries type information names a type the reader is not allowed to
 * instantiate.
 *
 * <p>This is a refusal rather than a failure: the document is well formed, but honouring it would
 * mean constructing whatever it asked for. Register the type if it is one of yours.
 */
public class JsonTypeNotAllowedException extends JsonReadException {

    private final String typeId;

    public JsonTypeNotAllowedException(String typeId, Throwable cause) {
        super("Type is not allowed to be deserialized: " + typeId, cause);
        this.typeId = typeId;
    }

    /**
     * The type named in the document, as it was named there.
     */
    public String typeId() {
        return typeId;
    }
}
