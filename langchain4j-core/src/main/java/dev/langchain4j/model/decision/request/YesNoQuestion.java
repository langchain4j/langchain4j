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
 * Each of them is a sufficient condition: when it holds, the answer follows it. When it does not hold, for example
 * when only {@code yesWhen} is set and the input does not meet it, the meaning of the question itself decides the
 * answer.
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
         * Describes when the answer should be "yes". This is a sufficient condition: when it holds, the answer is
         * "yes". When it does not hold, the answer is not necessarily "no": then the meaning of the question itself
         * decides, or {@link #noWhen(String)} if it is set and holds.
         */
        public Builder yesWhen(String description) {
            this.yesWhen = description;
            return this;
        }

        /**
         * Describes when the answer should be "no". This is a sufficient condition: when it holds, the answer is
         * "no". When it does not hold, the answer is not necessarily "yes": then the meaning of the question itself
         * decides, or {@link #yesWhen(String)} if it is set and holds.
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
