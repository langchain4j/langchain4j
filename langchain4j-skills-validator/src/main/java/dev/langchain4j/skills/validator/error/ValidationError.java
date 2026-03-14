package dev.langchain4j.skills.validator.error;

import java.util.List;

/**
 * Raised when skill properties are invalid.
 */
public class ValidationError extends SkillError {
    private final List<String> errors;

    public ValidationError(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ValidationError(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? List.copyOf(errors) : List.of(message);
    }

    public List<String> getErrors() {
        return errors;
    }
}
