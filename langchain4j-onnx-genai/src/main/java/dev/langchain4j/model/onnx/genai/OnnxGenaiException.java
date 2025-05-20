package dev.langchain4j.model.onnx.genai;

/**
 * Exception thrown when an error occurs during ONNX GenAI operations.
 */
public class OnnxGenaiException extends RuntimeException {

    /**
     * Creates a new OnnxGenaiException with the specified message.
     *
     * @param message The error message
     */
    public OnnxGenaiException(String message) {
        super(message);
    }

    /**
     * Creates a new OnnxGenaiException with the specified message and cause.
     *
     * @param message The error message
     * @param cause   The cause of the error
     */
    public OnnxGenaiException(String message, Throwable cause) {
        super(message, cause);
    }
}
