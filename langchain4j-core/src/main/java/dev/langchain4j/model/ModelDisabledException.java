package dev.langchain4j.model;

/**
 * An exception thrown by a model that could be disabled by a user.
 */
public class ModelDisabledException extends RuntimeException {
    public ModelDisabledException(String message) {
        super(message);
    }
}
