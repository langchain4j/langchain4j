package dev.langchain4j.service;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Thrown when content moderation fails, i.e., when content is flagged by the moderation model.
 *
 * @see Moderate
 * @see dev.langchain4j.model.moderation.ModerationModel
 */
public class ModerationException extends LangChain4jException {

    public ModerationException(String message) {
        super(message);
    }
}
