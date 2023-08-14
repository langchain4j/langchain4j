package dev.langchain4j.service;

/**
 * Thrown when content moderation fails, i.e., when content is flagged by the moderation model.
 *
 * @see Moderate
 * @see dev.langchain4j.model.moderation.ModerationModel
 */
public class ModerationException extends RuntimeException {

    public ModerationException(String message) {
        super(message);
    }
}
