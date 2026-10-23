package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * What a {@link Judge} evaluates: a text, the criteria to check it against and, optionally, the user message or the
 * conversation the text responds to and the sources it should be based on.
 * <pre>{@code
 * JudgeRequest request = JudgeRequest.builder()
 *         .text(answer)
 *         .userMessage(question)
 *         .sources(retrievedTexts)
 *         .criteria(
 *                 JudgeCriterion.from("says that the return period is 14 days"),
 *                 JudgeCriterion.from("contains only claims that are stated in the sources"))
 *         .build();
 * List<JudgeVerdict> verdicts = judge.evaluate(request);
 * }</pre>
 */
@Experimental
public final class JudgeRequest {

    private final String text;
    private final String userMessage;
    private final List<ChatMessage> conversation;
    private final List<String> sources;
    private final List<JudgeCriterion> criteria;

    private JudgeRequest(Builder builder) {
        this.text = ensureNotNull(builder.text, "text");
        this.userMessage = builder.userMessage == null ? null : ensureNotBlank(builder.userMessage, "userMessage");
        this.conversation = builder.conversation == null
                ? null
                : List.copyOf(ensureNotEmpty(builder.conversation, "conversation"));
        if (userMessage != null && conversation != null) {
            throw new IllegalArgumentException(
                    "Give either the user message or the conversation the text answers, not both: the conversation already contains the user message");
        }
        this.sources = builder.sources == null ? null : List.copyOf(builder.sources);
        this.criteria = List.copyOf(ensureNotEmpty(builder.criteria, "criteria"));
    }

    /**
     * Creates a builder of a request: the text to judge and the criteria are required.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The text to judge, for example the response of an AI Service.
     */
    public String text() {
        return text;
    }

    /**
     * The message the text responds to, or {@code null}.
     */
    public String userMessage() {
        return userMessage;
    }

    /**
     * The conversation the text answers, or {@code null}.
     */
    public List<ChatMessage> conversation() {
        return conversation;
    }

    /**
     * The sources the text should be based on, for example retrieved documents, or {@code null} if not given.
     * An empty list means the text had no sources to be based on.
     */
    public List<String> sources() {
        return sources;
    }

    /**
     * The criteria to check the text against, never empty.
     */
    public List<JudgeCriterion> criteria() {
        return criteria;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JudgeRequest that
                && text.equals(that.text)
                && Objects.equals(userMessage, that.userMessage)
                && Objects.equals(conversation, that.conversation)
                && Objects.equals(sources, that.sources)
                && criteria.equals(that.criteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, userMessage, conversation, sources, criteria);
    }

    @Override
    public String toString() {
        return "JudgeRequest{text=" + text + ", userMessage=" + userMessage + ", conversation=" + conversation
                + ", sources=" + sources + ", criteria=" + criteria + '}';
    }

    public static final class Builder {

        private String text;
        private String userMessage;
        private List<ChatMessage> conversation;
        private Collection<String> sources;
        private Collection<JudgeCriterion> criteria;

        private Builder() {}

        /**
         * The text to judge. Required.
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * The message the text responds to, so that the judge understands criteria that refer to the question. Only the
         * text is judged. Cannot be combined with {@link #conversation(List)}.
         */
        public Builder userMessage(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        /**
         * The conversation the text answers, for example the messages of the last request an AI Service sent to the
         * chat model: its system message, the messages from its chat memory, the user message and the tool calls and
         * results. It lets the judge check criteria that refer to the conversation, such as "uses only facts from the
         * tool results". Only the text is judged. Must not be empty. Cannot be combined with
         * {@link #userMessage(String)}.
         */
        public Builder conversation(List<? extends ChatMessage> conversation) {
            if (conversation != null) {
                conversation.forEach(message -> ensureNotNull(message, "message"));
            }
            this.conversation = conversation == null ? null : List.copyOf(conversation);
            return this;
        }

        /**
         * The sources the text should be based on, for example retrieved documents. They let the judge check criteria
         * such as "contains only claims that are stated in the sources".
         */
        public Builder sources(Collection<String> sources) {
            if (sources != null) {
                sources.forEach(source -> ensureNotNull(source, "source"));
            }
            this.sources = sources;
            return this;
        }

        /**
         * The criteria to check the text against. Required, not empty.
         */
        public Builder criteria(Collection<JudgeCriterion> criteria) {
            if (criteria != null) {
                criteria.forEach(criterion -> ensureNotNull(criterion, "criterion"));
            }
            this.criteria = criteria;
            return this;
        }

        /**
         * The criteria to check the text against. Required, not empty.
         */
        public Builder criteria(JudgeCriterion... criteria) {
            return criteria(criteria == null ? null : Arrays.asList(criteria));
        }

        /**
         * @throws IllegalArgumentException if the text or the criteria are missing, if the conversation is empty, or if
         *                                  both a user message and a conversation are given
         */
        public JudgeRequest build() {
            return new JudgeRequest(this);
        }
    }
}
