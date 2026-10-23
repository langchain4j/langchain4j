package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A criterion for a {@link Judge}, written in natural language, for example "mentions the refund amount".
 * Optionally, it defines when it passes and when it fails, which makes the verdicts more consistent and lets the team
 * agree on what the criterion means:
 * <pre>{@code
 * JudgeCriterion.from("mentions the refund amount")
 *         .withPassWhen("the response states the amount of money that will be refunded")
 *         .withFailWhen("the response only says that a refund will be made, without the amount");
 * }</pre>
 * A criterion is immutable: {@link #withPassWhen(String)} and {@link #withFailWhen(String)} return a new criterion.
 */
@Experimental
public final class JudgeCriterion {

    private final String text;
    private final String passWhen;
    private final String failWhen;

    private JudgeCriterion(String text, String passWhen, String failWhen) {
        this.text = ensureNotBlank(text, "criterion");
        this.passWhen = passWhen;
        this.failWhen = failWhen;
    }

    /**
     * Creates a criterion, for example {@code JudgeCriterion.from("is polite")}.
     */
    public static JudgeCriterion from(String text) {
        return new JudgeCriterion(text, null, null);
    }

    /**
     * Returns a copy of this criterion that defines when it passes.
     */
    public JudgeCriterion withPassWhen(String passWhen) {
        return new JudgeCriterion(text, ensureNotBlank(passWhen, "passWhen"), failWhen);
    }

    /**
     * Returns a copy of this criterion that defines when it fails.
     */
    public JudgeCriterion withFailWhen(String failWhen) {
        return new JudgeCriterion(text, passWhen, ensureNotBlank(failWhen, "failWhen"));
    }

    /**
     * The criterion, written in natural language, for example "is polite".
     */
    public String text() {
        return text;
    }

    /**
     * When this criterion passes, or {@code null} if not defined.
     */
    public String passWhen() {
        return passWhen;
    }

    /**
     * When this criterion fails, or {@code null} if not defined.
     */
    public String failWhen() {
        return failWhen;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JudgeCriterion that
                && text.equals(that.text)
                && Objects.equals(passWhen, that.passWhen)
                && Objects.equals(failWhen, that.failWhen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, passWhen, failWhen);
    }

    @Override
    public String toString() {
        return "JudgeCriterion{text=" + text + ", passWhen=" + passWhen + ", failWhen=" + failWhen + '}';
    }
}
