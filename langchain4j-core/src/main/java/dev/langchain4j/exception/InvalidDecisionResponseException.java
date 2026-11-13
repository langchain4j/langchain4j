package dev.langchain4j.exception;

import dev.langchain4j.Experimental;

/**
 * Thrown when a {@link dev.langchain4j.model.decision.DecisionModel} returns a response that does not match the
 * request, for example when an answer is missing, has the wrong type, or chooses an option that was not offered.
 *
 * @since 1.21.0
 */
@Experimental
public class InvalidDecisionResponseException extends LangChain4jException {

    public InvalidDecisionResponseException(String message) {
        super(message);
    }

    public InvalidDecisionResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
