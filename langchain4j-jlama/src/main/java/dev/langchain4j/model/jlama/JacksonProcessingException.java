package dev.langchain4j.model.jlama;

public class JacksonProcessingException extends RuntimeException {

    public JacksonProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
