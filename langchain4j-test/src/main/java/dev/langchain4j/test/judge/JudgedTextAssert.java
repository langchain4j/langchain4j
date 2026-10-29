package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.test.internal.ChatMessageTexts;
import dev.langchain4j.test.model.ChatRequestAssert;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.assertj.core.api.AbstractAssert;

/**
 * Assertions on a text, checked by a {@link Judge}. Created by {@link Judge#assertThat(String)}. Call
 * {@link #given(String)} or {@link #given(List)}, and {@link #basedOn(String...)} or
 * {@link #basedOnContents(Collection)},
 * if needed, before {@link #satisfies(String...)}, which can be called several times.
 * Like other AssertJ assertions, it supports {@code as(...)} descriptions, and soft assertions through
 * {@code softly.check(() -> judge.assertThat(answer).satisfies(...))}.
 */
@Experimental
public final class JudgedTextAssert extends AbstractAssert<JudgedTextAssert, String> {

    private static final int MAX_SOURCE_LENGTH = 300;
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final Judge judge;
    private JudgeContext context;
    private List<String> sources;
    private boolean judged;

    JudgedTextAssert(Judge judge, String text) {
        super(text, JudgedTextAssert.class);
        this.judge = ensureNotNull(judge, "judge");
    }

    /**
     * Gives the judge the text of the user message that the text responds to (for example
     * {@code userMessage.singleText()}), so that it understands criteria that refer to the question. Only the text is judged. Replaces a user message or conversation given before. Must be called before
     * {@code satisfies(...)}.
     *
     * @throws IllegalStateException if called after {@code satisfies(...)}
     */
    public JudgedTextAssert given(String userMessage) {
        ensureNotJudged("given(...)");
        this.context = JudgeContext.from(userMessage);
        return myself;
    }

    /**
     * Gives the judge the conversation that the text answers, so that it understands criteria that refer to it, for
     * example "uses only facts from the tool results". For the answer of an AI Service, these are the messages of the
     * last request it sent to the chat model: the system message, the messages from the chat memory, the user message
     * with the retrieved contents, and the tool calls and their results.
     * The requests can be collected by a {@code ChatModelListener} on the chat model of the AI Service (see
     * {@link ChatRequestAssert}):
     * <pre>{@code
     * String answer = assistant.chat("Cancel booking MS-777");
     *
     * Judge.from(judgeModel)
     *         .assertThat(answer)
     *         .given(requests.get(requests.size() - 1).messages())
     *         .satisfies("confirms what the cancellation tool returned");
     * }</pre>
     * Only the text is judged. Images and other media of the conversation (audio, video, PDF files) are sent to the
     * judge model too, so it must support them (see {@link ChatModelJudge}). Replaces a user message or conversation
     * given before. Must be called before
     * {@code satisfies(...)}. A judge backed by a {@code DecisionModel} receives the conversation as messages, with the
     * text as the last assistant message; it cannot combine a conversation with sources given by {@code basedOn(...)}.
     *
     * @throws IllegalStateException if called after {@code satisfies(...)}
     */
    public JudgedTextAssert given(List<? extends ChatMessage> conversation) {
        ensureNotJudged("given(...)");
        this.context = JudgeContext.from(conversation);
        return myself;
    }

    /**
     * Gives the judge the sources the text should be based on, for example retrieved documents, so that it can check
     * criteria such as "contains only claims that are stated in the sources". Must be called before
     * {@code satisfies(...)}.
     *
     * @throws IllegalStateException if called after {@code satisfies(...)}
     */
    public JudgedTextAssert basedOn(String... sources) {
        ensureNotNull(sources, "sources");
        return basedOn(Arrays.asList(sources));
    }

    /**
     * Gives the judge the sources the text should be based on, for example retrieved documents, so that it can check
     * criteria such as "contains only claims that are stated in the sources". Must be called before
     * {@code satisfies(...)}.
     *
     * @throws IllegalStateException if called after {@code satisfies(...)}
     */
    public JudgedTextAssert basedOn(Collection<String> sources) {
        ensureNotJudged("basedOn(...)");
        ensureNotNull(sources, "sources");
        sources.forEach(source -> ensureNotNull(source, "source"));
        this.sources = List.copyOf(sources);
        return myself;
    }

    /**
     * Gives the judge the sources the text should be based on, for example the sources retrieved by an AI Service
     * ({@code result.sources()}), so that it can check criteria such as "contains only claims that are stated in the
     * sources". Only the text of each source is given to the judge, and only the sources retrieved for this
     * invocation: in a conversation, the answer can also be based on sources retrieved for earlier messages.
     * Must be called before {@code satisfies(...)}.
     *
     * @throws IllegalStateException if called after {@code satisfies(...)}
     */
    public JudgedTextAssert basedOnContents(Collection<? extends Content> sources) {
        ensureNotJudged("basedOnContents(...)");
        ensureNotNull(sources, "sources");
        this.sources = sources.stream()
                .map(source -> ensureNotNull(ensureNotNull(source, "source").textSegment(), "source text")
                        .text())
                .toList();
        return myself;
    }

    /**
     * Asserts that the text satisfies every given criterion, according to the judge. A criterion the judge is not
     * sure about counts as not satisfied. Each call sends the text to the judge model. Without this call, nothing is
     * judged. A lambda, as in {@code satisfies(text -> ...)}, goes to AssertJ's own {@code satisfies} instead, which
     * does not call the judge.
     *
     * @param criteria criteria written in natural language, for example "is polite" or "does not mention prices".
     * @throws AssertionError listing each criterion that is not satisfied, with the judge's reasoning or probability.
     * @throws JudgeException if the judge model returned a response that could not be understood, or refused to
     *                        judge, or if the judge did not return one verdict per criterion. Exceptions thrown by
     *                        the judge model are not caught.
     * @throws IllegalArgumentException if the judge cannot judge this request, for example a {@link DecisionModelJudge}
     *                                  given both a conversation and sources; nothing is sent to the judge model then.
     */
    public JudgedTextAssert satisfies(String... criteria) {
        ensureNotEmpty(criteria, "criteria");
        return satisfies(Arrays.stream(criteria).map(JudgeCriterion::from).toArray(JudgeCriterion[]::new));
    }

    /**
     * Asserts that the text satisfies every given criterion, according to the judge. Use this method instead of
     * {@link #satisfies(String...)} when a criterion defines when it passes and when it fails.
     *
     * @throws AssertionError listing each criterion that is not satisfied, with the judge's reasoning or probability.
     * @throws JudgeException if the judge model returned a response that could not be understood, or refused to
     *                        judge, or if the judge did not return one verdict per criterion. Exceptions thrown by
     *                        the judge model are not caught.
     * @throws IllegalArgumentException if the judge cannot judge this request, for example a {@link DecisionModelJudge}
     *                                  given both a conversation and sources; nothing is sent to the judge model then.
     */
    public JudgedTextAssert satisfies(JudgeCriterion... criteria) {
        ensureNotEmpty(criteria, "criteria");
        isNotNull();
        judged = true;

        JudgeRequest request = JudgeRequest.builder()
                .text(actual)
                .context(context)
                .sources(sources)
                .criteria(criteria)
                .build();
        JudgeResponse response = judge.evaluate(request);
        if (response == null) {
            throw new JudgeException("The judge %s returned no response".formatted(judge));
        }
        List<JudgeVerdict> verdicts = response.verdicts();
        ensureOneVerdictPerCriterion(verdicts, request.criteria());
        List<JudgeVerdict> failed = verdicts.stream()
                .filter(verdict -> !verdict.isSatisfied())
                .toList();
        if (!failed.isEmpty()) {
            throw failure("%s", failureMessage(criteria.length, failed));
        }
        return myself;
    }

    private void ensureOneVerdictPerCriterion(List<JudgeVerdict> verdicts, List<JudgeCriterion> criteria) {
        if (verdicts.size() != criteria.size()) {
            throw new JudgeException("The judge %s returned %d verdicts for %d criteria: it must return one verdict per criterion"
                    .formatted(judge, verdicts.size(), criteria.size()));
        }
        for (int i = 0; i < criteria.size(); i++) {
            if (!criteria.get(i).equals(verdicts.get(i).criterion())) {
                throw new JudgeException(
                        "The judge %s returned the verdicts in another order or for other criteria: verdict %d is for \"%s\", but criterion %d is \"%s\""
                                .formatted(judge, i + 1, verdicts.get(i).criterion().text(), i + 1, criteria.get(i).text()));
            }
        }
    }

    private void ensureNotJudged(String method) {
        if (judged) {
            throw new IllegalStateException(method + " must be called before satisfies(...)");
        }
    }

    private String failureMessage(int criterionCount, List<JudgeVerdict> failed) {
        String newLine = System.lineSeparator();
        StringBuilder message = new StringBuilder("Expected the text to satisfy %d criteria, but %d did not:"
                .formatted(criterionCount, failed.size()));
        for (JudgeVerdict verdict : failed) {
            message.append(newLine)
                    .append(newLine)
                    .append("Criterion: ")
                    .append(verdict.criterion().text());
            if (verdict.criterion().satisfiedWhen() != null) {
                message.append(newLine).append("Satisfied when: ").append(verdict.criterion().satisfiedWhen());
            }
            if (verdict.criterion().notSatisfiedWhen() != null) {
                message.append(newLine).append("Not satisfied when: ").append(verdict.criterion().notSatisfiedWhen());
            }
            message.append(newLine)
                    .append("Verdict: ")
                    .append(verdict.explanation());
        }
        message.append(newLine).append(newLine).append("Text:").append(newLine).append(actual);
        if (context instanceof UserMessageContext userMessage) {
            message.append(newLine)
                    .append(newLine)
                    .append("User message:")
                    .append(newLine)
                    .append(ChatMessageTexts.truncate(userMessage.text(), MAX_MESSAGE_LENGTH));
        }
        if (context instanceof ConversationContext conversation) {
            message.append(newLine)
                    .append(newLine)
                    .append("Conversation:")
                    .append(newLine)
                    .append(ChatMessageTexts.describe(conversation.messages(), MAX_MESSAGE_LENGTH));
        }
        if (sources != null) {
            message.append(newLine).append(newLine).append("Sources:");
            if (sources.isEmpty()) {
                message.append(" none");
            }
            for (int i = 0; i < sources.size(); i++) {
                message.append(newLine)
                        .append("%d. %s".formatted(i + 1, ChatMessageTexts.truncate(sources.get(i), MAX_SOURCE_LENGTH)));
            }
        }
        return message.toString();
    }
}
