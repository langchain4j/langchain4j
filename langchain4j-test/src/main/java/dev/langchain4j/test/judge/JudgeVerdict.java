package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * The verdict of a {@link Judge} on one {@link JudgeCriterion}. Returned by {@link Judge#evaluate(JudgeRequest)}.
 * Implementations of {@link Judge} create verdicts with {@link #from(JudgeCriterion, boolean, String)}.
 */
@Experimental
public final class JudgeVerdict {

    private final JudgeCriterion criterion;
    private final boolean satisfied;
    private final String explanation;
    private final Double probability;

    private JudgeVerdict(JudgeCriterion criterion, boolean satisfied, String explanation, Double probability) {
        this.criterion = ensureNotNull(criterion, "criterion");
        this.satisfied = satisfied;
        this.explanation = ensureNotBlank(explanation, "explanation");
        this.probability = probability == null ? null : ensureBetween(probability, 0, 1, "probability");
    }

    /**
     * Creates a verdict without a probability, for example in a {@link Judge} implementation.
     *
     * @param criterion   the criterion the verdict is about
     * @param satisfied   whether the criterion is satisfied
     * @param explanation a human-readable explanation of the verdict
     */
    public static JudgeVerdict from(JudgeCriterion criterion, boolean satisfied, String explanation) {
        return new JudgeVerdict(criterion, satisfied, explanation, null);
    }

    /**
     * Creates a verdict with the probability, from 0 to 1, that the criterion is satisfied, for example in a
     * {@link Judge} implementation.
     *
     * @param criterion   the criterion the verdict is about
     * @param satisfied   whether the criterion is satisfied
     * @param explanation a human-readable explanation of the verdict
     * @param probability the probability that the criterion is satisfied, from 0 to 1
     */
    public static JudgeVerdict from(JudgeCriterion criterion, boolean satisfied, String explanation, double probability) {
        return new JudgeVerdict(criterion, satisfied, explanation, probability);
    }

    /**
     * The criterion this verdict is about.
     */
    public JudgeCriterion criterion() {
        return criterion;
    }

    /**
     * Whether the judge considers the criterion satisfied. A criterion the judge was not sure about is not
     * satisfied.
     */
    public boolean isSatisfied() {
        return satisfied;
    }

    /**
     * A human-readable explanation of the verdict: the judge's reasoning for a {@link ChatModelJudge}, the probability
     * and threshold for a {@link DecisionModelJudge}.
     */
    public String explanation() {
        return explanation;
    }

    /**
     * The probability, from 0 to 1, that the criterion is satisfied, as answered by a {@link DecisionModelJudge}, or
     * {@code null} if the judge does not provide one, for example a {@link ChatModelJudge}.
     */
    public Double probability() {
        return probability;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JudgeVerdict that
                && satisfied == that.satisfied
                && criterion.equals(that.criterion)
                && explanation.equals(that.explanation)
                && Objects.equals(probability, that.probability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(criterion, satisfied, explanation, probability);
    }

    @Override
    public String toString() {
        return "JudgeVerdict{criterion=" + criterion + ", satisfied=" + satisfied + ", explanation=" + explanation
                + ", probability=" + probability + '}';
    }
}
