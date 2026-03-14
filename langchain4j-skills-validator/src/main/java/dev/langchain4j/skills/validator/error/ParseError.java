package dev.langchain4j.skills.validator.error;

/**
 * Raised when SKILL.md parsing fails.
 */
public class ParseError extends SkillError {
    public ParseError(String message) {
        super(message);
    }

    public ParseError(String message, Throwable cause) {
        super(message, cause);
    }
}
