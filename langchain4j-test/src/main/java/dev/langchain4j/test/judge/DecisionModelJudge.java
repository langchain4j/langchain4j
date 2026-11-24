package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.test.internal.ChatMessageTexts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * A {@link Judge} backed by a {@link DecisionModel}. Each criterion is asked as a yes/no question, and is satisfied
 * when the probability of "yes" is at least the threshold, 0.5 by default. Unless the criterion defines when it
 * passes and fails, the question tells the model to answer "no" when the criterion is satisfied only partly or when
 * it is unclear, so a passing criterion means the model considers it clearly satisfied. All criteria of an assertion
 * are asked in a single request; whether the answer to one criterion can be influenced by the others depends on the
 * decision model. So does how closely the model follows the pass and fail definitions of a {@link JudgeCriterion}: check
 * a few known cases before relying on them.
 * Created by {@link Judge#from(DecisionModel)}.
 * <p>
 * A conversation given with {@link JudgedTextAssert#given(List)} is sent to the decision model as messages, with its
 * images and other media: an image whose detail level was not chosen when it was created (see
 * {@link ImageContent#detailLevelSpecified()}) is sent with
 * {@link ImageContent.DetailLevel#AUTO}, an image with a chosen detail level is sent as it is, the same way as by a
 * {@link ChatModelJudge}. An image read back from JSON, for example from a persistent chat memory store, counts as
 * having a chosen detail level, so it keeps {@link ImageContent.DetailLevel#LOW}.
 */
@Experimental
public final class DecisionModelJudge implements Judge {

    static final double DEFAULT_THRESHOLD = 0.5;
    private static final Function<JudgeCriterion, YesNoQuestion> DEFAULT_QUESTIONS = DecisionModelJudge::defaultQuestion;

    private final DecisionModel decisionModel;
    private final double threshold;
    private final Function<JudgeCriterion, YesNoQuestion> questions;

    DecisionModelJudge(DecisionModel decisionModel, double threshold, Function<JudgeCriterion, YesNoQuestion> questions) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        if (!(threshold > 0 && threshold <= 1)) {
            throw illegalArgument("threshold must be greater than 0 and at most 1, but is %s", threshold);
        }
        this.threshold = threshold;
        this.questions = ensureNotNull(questions, "questions");
    }

    static DecisionModelJudge from(DecisionModel decisionModel) {
        return new DecisionModelJudge(decisionModel, DEFAULT_THRESHOLD, DEFAULT_QUESTIONS);
    }

    /**
     * Returns a judge with the given threshold: a criterion is satisfied when the probability of "yes" is greater
     * than or equal to it.
     *
     * @param threshold greater than 0 and at most 1.
     */
    public DecisionModelJudge withThreshold(double threshold) {
        return new DecisionModelJudge(decisionModel, threshold, questions);
    }

    /**
     * Returns a judge that asks each criterion with the question returned by the given function, instead of the
     * default question. The decision model receives the text to judge as {@code response}, and, if given, the user
     * message as {@code userMessage} and the sources as {@code sources}: the question can refer to them by these names.
     * When a conversation is given (see {@link JudgedTextAssert#given(List)}), the decision model instead receives the
     * conversation followed by the text to judge as the last assistant message, so the question should refer to "the
     * last assistant message". For example:
     * <pre>{@code
     * DecisionModelJudge judge = Judge.from(decisionModel).withQuestion(criterion -> YesNoQuestion.builder()
     *         .text("Would a support agent accept this response? It must: " + criterion.text())
     *         .yesWhen(criterion.satisfiedWhen() != null ? criterion.satisfiedWhen() : "it clearly does")
     *         .noWhen(criterion.notSatisfiedWhen() != null ? criterion.notSatisfiedWhen() : "it does not, or only partly")
     *         .build());
     * }</pre>
     * A judge can be created once and shared by all tests, for example in a constant or a test configuration.
     * The default question was chosen by measuring how often judges give wrong verdicts with it. Check your question
     * on a few cases with known verdicts before relying on it.
     *
     * @param questions returns the question to ask for a criterion
     */
    public DecisionModelJudge withQuestion(Function<JudgeCriterion, YesNoQuestion> questions) {
        return new DecisionModelJudge(decisionModel, threshold, questions);
    }

    @Override
    public JudgeResponse evaluate(JudgeRequest judgeRequest) {
        ensureNotNull(judgeRequest, "request");
        DecisionRequest.Builder request = DecisionRequest.builder();
        Function<JudgeCriterion, YesNoQuestion> questions = this.questions;
        JudgeContext context = judgeRequest.context();
        if (context instanceof ConversationContext conversation) {
            ensureNoSources(judgeRequest);
            List<ChatMessage> messages = new ArrayList<>();
            conversation.messages().forEach(message -> messages.add(ChatMessageTexts.forJudge(message)));
            messages.add(AiMessage.from(judgeRequest.text()));
            request.inputMessages(messages);
            if (questions == DEFAULT_QUESTIONS) {
                questions = DecisionModelJudge::defaultConversationQuestion;
            }
        } else if (context == null || context instanceof UserMessageContext) {
            Map<String, Object> input = new LinkedHashMap<>();
            if (context instanceof UserMessageContext userMessage) {
                input.put("userMessage", userMessage.text());
            }
            if (judgeRequest.sources() != null) {
                input.put("sources", judgeRequest.sources());
            }
            input.put("response", judgeRequest.text());
            request.inputFields(input);
        } else {
            throw illegalArgument(
                    "Unsupported JudgeContext %s: create it with JudgeContext.from(String) for a user message or"
                            + " JudgeContext.from(List) for a conversation",
                    context.getClass().getName());
        }

        List<JudgeCriterion> criteria = judgeRequest.criteria();
        for (int i = 0; i < criteria.size(); i++) {
            YesNoQuestion question = questions.apply(criteria.get(i));
            if (question == null) {
                throw illegalArgument("The question for criterion %d is null", i + 1);
            }
            request.question(questionName(i), question);
        }
        DecisionResponse response;
        try {
            response = decisionModel.decide(request.build());
        } catch (InvalidDecisionResponseException e) {
            throw new JudgeException("The judge returned an invalid response: " + e.getMessage(), e);
        } catch (ContentFilteredException e) {
            throw new JudgeException("The judge model refused to answer (content filter): " + e.getMessage(), e);
        }

        List<JudgeVerdict> verdicts = new ArrayList<>();
        for (int i = 0; i < criteria.size(); i++) {
            String name = questionName(i);
            YesNoAnswer answer;
            try {
                if (response.isRefused(name)) {
                    throw new JudgeException("The judge model refused to judge criterion %d".formatted(i + 1));
                }
                answer = response.yesNo(name);
            } catch (IllegalArgumentException e) {
                throw new JudgeException(
                        "The judge returned no answer for criterion %d: %s".formatted(i + 1, e.getMessage()), e);
            }
            verdicts.add(JudgeVerdict.from(
                    criteria.get(i),
                    answer.isYes(threshold),
                    String.format(Locale.ROOT, "P(yes) = %.3f, threshold = %.3f", answer.probability(), threshold),
                    answer.probability()));
        }
        return JudgeResponse.builder()
                .verdicts(verdicts)
                .tokenUsage(response.tokenUsage())
                .build();
    }

    private static void ensureNoSources(JudgeRequest judgeRequest) {
        if (judgeRequest.sources() != null && !judgeRequest.sources().isEmpty()) {
            throw illegalArgument(
                    "A DecisionModelJudge cannot judge a text given a conversation and separate sources: put the"
                            + " sources in the conversation (the requests of an AI Service already contain the"
                            + " retrieved content), or give the user message instead of the conversation");
        }
    }

    private static YesNoQuestion defaultQuestion(JudgeCriterion criterion) {
        return YesNoQuestion.builder()
                .text("Does the response satisfy this condition: " + criterion.text())
                .yesWhen(getOrDefault(criterion.satisfiedWhen(), "the response clearly and fully satisfies the condition"))
                .noWhen(getOrDefault(
                        criterion.notSatisfiedWhen(),
                        "the response does not satisfy the condition, satisfies it only partly, or it is unclear"))
                .build();
    }

    private static YesNoQuestion defaultConversationQuestion(JudgeCriterion criterion) {
        return YesNoQuestion.builder()
                .text("Does the last assistant message of the conversation satisfy this condition: " + criterion.text())
                .yesWhen(getOrDefault(
                        criterion.satisfiedWhen(), "the last assistant message clearly and fully satisfies the condition"))
                .noWhen(getOrDefault(
                        criterion.notSatisfiedWhen(),
                        "the last assistant message does not satisfy the condition, satisfies it only partly, or it is"
                                + " unclear"))
                .build();
    }

    private static String questionName(int index) {
        return "condition" + (index + 1);
    }
}
