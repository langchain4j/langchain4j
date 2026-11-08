package dev.langchain4j.model.decision.response;

import dev.langchain4j.Experimental;

/**
 * The answer to a question that the decision model refused to answer, for example because the input or the question
 * goes against the usage policies of the provider. The other questions of the request can still have regular answers.
 * <p>
 * Use {@link DecisionResponse#isRefused(String)} to check whether a question was refused: the typed accessors of
 * {@link DecisionResponse}, such as {@link DecisionResponse#yesNo(String)}, throw a
 * {@link dev.langchain4j.exception.ContentFilteredException} for a refused question.
 *
 * @since 1.22.0
 */
@Experimental
public final class RefusalAnswer implements DecisionAnswer {

    private static final RefusalAnswer INSTANCE = new RefusalAnswer();

    private RefusalAnswer() {}

    /**
     * Returns the answer to a refused question.
     */
    public static RefusalAnswer of() {
        return INSTANCE;
    }

    @Override
    public String toString() {
        return "RefusalAnswer";
    }
}
