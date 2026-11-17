package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A yes/no question, answered with the probability that the answer is "yes"
 * (see {@link dev.langchain4j.model.decision.response.YesNoAnswer}).
 * <p>
 * Optionally, {@link #yesWhen()} and {@link #noWhen()} describe when the answer should be "yes" and when it
 * should be "no":
 * <pre>{@code
 * YesNoQuestion refundRequested = YesNoQuestion.builder()
 *         .text("Does the customer ask for a refund?")
 *         .yesWhen("The customer explicitly asks for their money back")
 *         .noWhen("The customer only asks about a charge")
 *         .build();
 * }</pre>
 * They describe what "yes" and "no" mean for this question when the question alone is ambiguous. When only one of
 * them is set, how it combines with the question is up to the model: some models read a lone {@code yesWhen} as the
 * only reason to answer "yes". To split the cases clearly, set both.
 *
 * @since 1.21.0
 */
@Experimental
public final class YesNoQuestion implements Question {

    private final String text;
    private final String yesWhen;
    private final String noWhen;

    private YesNoQuestion(Builder builder) {
        this.text = ensureNotBlank(builder.text, "text");
        this.yesWhen = builder.yesWhen == null ? null : ensureNotBlank(builder.yesWhen, "yesWhen");
        this.noWhen = builder.noWhen == null ? null : ensureNotBlank(builder.noWhen, "noWhen");
    }

    @Override
    public String text() {
        return text;
    }

    /**
     * When the answer should be "yes", or {@code null} if not set.
     */
    public String yesWhen() {
        return yesWhen;
    }

    /**
     * When the answer should be "no", or {@code null} if not set.
     */
    public String noWhen() {
        return noWhen;
    }

    /**
     * Creates a yes/no question with the given text.
     *
     * @since 1.23.0
     */
    public static YesNoQuestion from(String text) {
        return builder().text(text).build();
    }

    /**
     * Creates a yes/no question with the given text.
     *
     * @deprecated use {@link #from(String)} instead
     */
    @Deprecated(since = "1.23.0", forRemoval = true)
    public static YesNoQuestion of(String text) {
        return from(text);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YesNoQuestion that)) return false;
        return Objects.equals(text, that.text)
                && Objects.equals(yesWhen, that.yesWhen)
                && Objects.equals(noWhen, that.noWhen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, yesWhen, noWhen);
    }

    @Override
    public String toString() {
        return "YesNoQuestion{text=" + text + ", yesWhen=" + yesWhen + ", noWhen=" + noWhen
                + '}';
    }

    public static final class Builder {

        private String text;
        private String yesWhen;
        private String noWhen;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Describes when the answer should be "yes" (see {@link YesNoQuestion} for how it combines with the question).
         */
        public Builder yesWhen(String description) {
            this.yesWhen = description;
            return this;
        }

        /**
         * Describes when the answer should be "no" (see {@link YesNoQuestion} for how it combines with the question).
         */
        public Builder noWhen(String description) {
            this.noWhen = description;
            return this;
        }

        public YesNoQuestion build() {
            return new YesNoQuestion(this);
        }
    }
}
