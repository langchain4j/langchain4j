package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A criterion for a {@link Judge}, written in natural language, for example "mentions the refund amount".
 * Optionally, it defines when it is satisfied and when it is not, which makes the verdicts more consistent and lets the team
 * agree on what the criterion means:
 * <pre>{@code
 * JudgeCriterion.from("mentions the refund amount")
 *         .satisfiedWhen("the response states the amount of money that will be refunded")
 *         .notSatisfiedWhen("the response only says that a refund will be made, without the amount");
 * }</pre>
 * A criterion is immutable: {@link #satisfiedWhen(String)} and {@link #notSatisfiedWhen(String)} return a new
 * criterion.
 */
@Experimental
public final class JudgeCriterion {

    private final String text;
    private final String satisfiedWhen;
    private final String notSatisfiedWhen;

    private JudgeCriterion(String text, String satisfiedWhen, String notSatisfiedWhen) {
        this.text = ensureNotBlank(text, "criterion");
        this.satisfiedWhen = satisfiedWhen;
        this.notSatisfiedWhen = notSatisfiedWhen;
    }

    /**
     * Creates a criterion, for example {@code JudgeCriterion.from("is polite")}.
     */
    public static JudgeCriterion from(String text) {
        return new JudgeCriterion(text, null, null);
    }

    /**
     * Returns a copy of this criterion that defines when it is satisfied.
     */
    public JudgeCriterion satisfiedWhen(String satisfiedWhen) {
        return new JudgeCriterion(text, ensureNotBlank(satisfiedWhen, "satisfiedWhen"), notSatisfiedWhen);
    }

    /**
     * Returns a copy of this criterion that defines when it is not satisfied.
     */
    public JudgeCriterion notSatisfiedWhen(String notSatisfiedWhen) {
        return new JudgeCriterion(text, satisfiedWhen, ensureNotBlank(notSatisfiedWhen, "notSatisfiedWhen"));
    }

    /**
     * The criterion, written in natural language, for example "is polite".
     */
    public String text() {
        return text;
    }

    /**
     * When this criterion is satisfied, or {@code null} if not defined.
     */
    public String satisfiedWhen() {
        return satisfiedWhen;
    }

    /**
     * When this criterion is not satisfied, or {@code null} if not defined.
     */
    public String notSatisfiedWhen() {
        return notSatisfiedWhen;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JudgeCriterion that
                && text.equals(that.text)
                && Objects.equals(satisfiedWhen, that.satisfiedWhen)
                && Objects.equals(notSatisfiedWhen, that.notSatisfiedWhen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, satisfiedWhen, notSatisfiedWhen);
    }

    @Override
    public String toString() {
        return "JudgeCriterion{text=" + text + ", satisfiedWhen=" + satisfiedWhen + ", notSatisfiedWhen="
                + notSatisfiedWhen + '}';
    }
}
