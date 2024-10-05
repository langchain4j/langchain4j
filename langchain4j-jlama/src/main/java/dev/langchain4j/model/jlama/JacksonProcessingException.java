package dev.langchain4j.model.jlama;

public class JacksonProcessingException extends RuntimeException {

    public JacksonProcessingException(String message) {
        super(message);
    }

    public JacksonProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public JacksonProcessingException(Throwable cause) {
        super(cause);
    }
}
