package dev.langchain4j.model.ollama;

public class OllamaHttpException extends RuntimeException {

    public OllamaHttpException(String message) {
        super(message);
    }

    public OllamaHttpException(Throwable cause) {
        super(cause);
    }
}
