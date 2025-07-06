package dev.langchain4j.service.guardrail;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
