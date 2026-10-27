package dev.langchain4j.test.judge;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.LangChain4jException;

/**
 * Thrown when a {@link Judge} could not judge a text, for example because the judge model returned a response that
 * could not be understood. It is not an {@link AssertionError}, so that a test framework reports it as an error of
 * the judge rather than as a failed check of the text.
 */
@Experimental
public class JudgeException extends LangChain4jException {

    /**
     * @param message why the text could not be judged
     */
    public JudgeException(String message) {
        super(message);
    }

    /**
     * @param message why the text could not be judged
     * @param cause   the exception that prevented it
     */
    public JudgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
