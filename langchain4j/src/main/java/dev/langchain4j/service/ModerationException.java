package dev.langchain4j.service;

public class ModerationException extends RuntimeException {

    public ModerationException(String message) {
        super(message);
    }
}
