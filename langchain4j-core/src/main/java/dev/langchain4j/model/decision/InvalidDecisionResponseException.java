package dev.langchain4j.model.decision;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.LangChain4jException;

/**
 * Thrown when a decision model returns a response that does not match the request, for example when an answer is
 * missing, has the wrong type, or chooses an option that was not offered.
 *
 * @since 1.21.0
 */
@Experimental
public class InvalidDecisionResponseException extends LangChain4jException {

    public InvalidDecisionResponseException(String message) {
        super(message);
    }
}
