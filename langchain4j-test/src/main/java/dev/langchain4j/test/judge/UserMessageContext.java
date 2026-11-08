package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;

/**
 * A {@link JudgeContext} with the text of the user message that the judged text responds to, for example
 * {@code userMessage.singleText()}.
 */
@Experimental
public final class UserMessageContext implements JudgeContext {

    private final String text;

    private UserMessageContext(String text) {
        this.text = ensureNotBlank(text, "userMessage");
    }

    /**
     * Creates a context with the given user message text.
     *
     * @throws IllegalArgumentException if the text is blank
     */
    public static UserMessageContext from(String userMessage) {
        return new UserMessageContext(userMessage);
    }

    /**
     * The text of the user message, never blank.
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserMessageContext that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return "UserMessageContext{text=" + text + '}';
    }
}
