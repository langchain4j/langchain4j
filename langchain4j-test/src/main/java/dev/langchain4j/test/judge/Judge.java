package dev.langchain4j.test.judge;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import java.util.List;
import java.util.function.Function;

/**
 * Checks whether a text, for example the response of an AI Service, satisfies criteria written in natural language,
 * using a model as the judge:
 * <pre>{@code
 * Judge judge = Judge.from(judgeModel);
 *
 * judge.assertThat(answer)
 *         .given(question)
 *         .satisfies("says that the booking cannot be found", "does not mention any dates");
 * }</pre>
 * The judge can be a {@link ChatModel}, which explains each verdict, or a {@link DecisionModel}, which answers each
 * criterion with the probability that it is satisfied. The assertion fails when at least one criterion is not
 * satisfied, and the message names each failed criterion with the judge's reasoning or probability. A judge is
 * immutable: create it once and reuse it across tests.
 * <p>
 * To check that a RAG answer is grounded, give the judge the retrieved sources:
 * <pre>{@code
 * judge.assertThat(result.content())
 *         .basedOnContents(result.sources())
 *         .satisfies("contains only claims that are stated in the sources");
 * }</pre>
 * Prefer this wording to "is supported by the sources": judges often pass that one even when the text adds a claim
 * that is not in the sources.
 * <p>
 * Vague criteria give inconsistent verdicts. Write one specific criterion per thing that can go wrong, say exactly
 * what you mean (for example "says that the return period is 14 days" rather than "answers the question") and, when
 * its meaning could be disputed, define when it passes and fails with {@link JudgeCriterion}.
 * <p>
 * Every assertion calls the judge model, so it takes time and costs money like any other model call (to track the
 * cost, add a {@code ChatModelListener} to the judge model). Judges make mistakes and their answers can vary between
 * runs. Use a capable model, preferably from a different model family than the one being tested, pin a specific model
 * version (not an alias the provider can point to a newer model) so that results stay comparable, set its
 * temperature to 0 where the model supports it, and prefer criteria that can be judged from what the judge is given.
 * For a {@link ChatModel} judge, enable strict JSON schema where the provider supports it (for example
 * {@code OpenAiChatModel.builder().strictJsonSchema(true)}), so that the model follows the response format exactly.
 * <p>
 * Use {@link #assertThat(String)} in tests. Use {@link #evaluate(JudgeRequest)} to get the verdicts as values, for
 * example to compute pass rates over a dataset. To use another way of judging, for example several judges that vote,
 * implement {@link #evaluate(JudgeRequest)} and return {@link JudgeResponse#from(List)}: {@link #assertThat(String)}
 * works with any implementation.
 */
@Experimental
public interface Judge {

    /**
     * Returns a judge backed by the given chat model. By default, all criteria of one assertion are judged in a
     * single request. The model must support JSON schema response formats ({@code ResponseFormat} with a
     * {@code JsonSchema}).
     *
     * @see ChatModelJudge#withOneRequestPerCriterion()
     * @see ChatModelJudge#withSystemMessage(String)
     */
    static ChatModelJudge from(ChatModel chatModel) {
        return ChatModelJudge.from(chatModel);
    }

    /**
     * Returns a judge backed by the given decision model: each criterion is asked as a yes/no question, all in a
     * single request.
     *
     * @see DecisionModelJudge#withThreshold(double)
     * @see DecisionModelJudge#withQuestion(Function)
     */
    static DecisionModelJudge from(DecisionModel decisionModel) {
        return DecisionModelJudge.from(decisionModel);
    }

    /**
     * Judges the text of the request against each criterion, without failing.
     *
     * @return one verdict per criterion, in the order of the criteria (a criterion the judge was not sure about is
     *         not satisfied), and the tokens the judge model used
     * @throws JudgeException           if the judge model returned a response that could not be understood, or
     *                                  refused to judge
     * @throws IllegalArgumentException if the judge cannot judge this request, for example a
     *                                  {@link DecisionModelJudge} given both a conversation and sources
     */
    JudgeResponse evaluate(JudgeRequest request);

    /**
     * Starts assertions on the given text, for example the response of an AI Service. Nothing is judged until
     * {@link JudgedTextAssert#satisfies(String...)} is called: a chain without it checks nothing. A lambda passed to
     * {@code satisfies(...)} goes to AssertJ's own {@code satisfies}, not to the judge.
     */
    default JudgedTextAssert assertThat(String text) {
        return new JudgedTextAssert(this, text);
    }
}
