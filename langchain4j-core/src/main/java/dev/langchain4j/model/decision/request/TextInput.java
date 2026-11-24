package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A {@link DecisionInput} that is plain text, for example a support ticket.
 *
 * @since 1.23.0
 */
@Experimental
public final class TextInput implements DecisionInput {

    private final String text;

    private TextInput(String text) {
        this.text = ensureNotBlank(text, "text");
    }

    /**
     * @throws IllegalArgumentException if {@code text} is {@code null} or blank
     */
    public static TextInput from(String text) {
        return new TextInput(text);
    }

    public String text() {
        return text;
    }

    @Override
    public DecisionInputType type() {
        return DecisionInputType.TEXT;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextInput that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "TextInput{length=" + text.length() + '}';
    }
}
