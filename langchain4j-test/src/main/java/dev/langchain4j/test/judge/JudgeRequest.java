package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * What a {@link Judge} evaluates: a text, the criteria to check it against and, optionally, the
 * {@link JudgeContext context} the text responds to (a user message or a conversation) and the sources it should be
 * based on.
 * <pre>{@code
 * JudgeRequest request = JudgeRequest.builder()
 *         .text(answer)
 *         .context(JudgeContext.from(question))
 *         .sources(retrievedTexts)
 *         .criteria(
 *                 JudgeCriterion.from("says that the return period is 14 days"),
 *                 JudgeCriterion.from("contains only claims that are stated in the sources"))
 *         .build();
 * List<JudgeVerdict> verdicts = judge.evaluate(request).verdicts();
 * }</pre>
 */
@Experimental
public final class JudgeRequest {

    private final String text;
    private final JudgeContext context;
    private final List<String> sources;
    private final List<JudgeCriterion> criteria;

    private JudgeRequest(Builder builder) {
        this.text = ensureNotNull(builder.text, "text");
        this.context = builder.context;
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
     * What the text responds to (a {@link UserMessageContext} or a {@link ConversationContext}), or {@code null} if
     * not given.
     */
    public JudgeContext context() {
        return context;
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
                && Objects.equals(context, that.context)
                && Objects.equals(sources, that.sources)
                && criteria.equals(that.criteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, context, sources, criteria);
    }

    @Override
    public String toString() {
        return "JudgeRequest{text=" + text + ", context=" + context + ", sources=" + sources + ", criteria="
                + criteria + '}';
    }

    public static final class Builder {

        private String text;
        private JudgeContext context;
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
         * What the text responds to, created with {@link JudgeContext#from(String)} for a user message or
         * {@link JudgeContext#from(List)} for a conversation. It lets the judge understand criteria that refer to the
         * question or the conversation. Only the text is judged. Optional.
         */
        public Builder context(JudgeContext context) {
            this.context = context;
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
         * @throws IllegalArgumentException if the text or the criteria are missing
         */
        public JudgeRequest build() {
            return new JudgeRequest(this);
        }
    }
}
