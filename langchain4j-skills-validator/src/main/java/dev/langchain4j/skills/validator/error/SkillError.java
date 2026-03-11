package dev.langchain4j.skills.validator.error;

/**
 * Base exception for all skill-related errors.
 */
public class SkillError extends Exception {
    public SkillError(String message) {
        super(message);
    }

    public SkillError(String message, Throwable cause) {
        super(message, cause);
    }
}
