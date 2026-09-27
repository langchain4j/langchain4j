package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A yes/no question, answered with the probability that the answer is "yes"
 * (see {@link dev.langchain4j.model.decision.response.YesNoAnswer}).
 * <p>
 * Optionally, {@link #yesWhen()} and {@link #noWhen()} describe when the answer should be "yes" and when it
 * should be "no". Each description is either plain text or structured content (a {@link java.util.Map} or a
 * {@link java.util.List}) that is passed to the model as is:
 * <pre>{@code
 * YesNoQuestion refundRequested = YesNoQuestion.builder()
 *         .text("Does the customer ask for a refund?")
 *         .yesWhen("The customer explicitly asks for their money back")
 *         .noWhen("The customer only asks about a charge")
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class YesNoQuestion implements Question {

    private final String text;
    private final Object yesWhen;
    private final Object noWhen;

    private YesNoQuestion(Builder builder) {
        this.text = ensureNotBlank(builder.text, "text");
        this.yesWhen = builder.yesWhen == null ? null : FreeFormValue.ensureValid(builder.yesWhen, "yesWhen");
        this.noWhen = builder.noWhen == null ? null : FreeFormValue.ensureValid(builder.noWhen, "noWhen");
    }

    @Override
    public String text() {
        return text;
    }

    /**
     * When the answer should be "yes": a {@link String}, a {@link java.util.Map} or a {@link java.util.List}, or
     * {@code null} if not set.
     */
    public Object yesWhen() {
        return yesWhen;
    }

    /**
     * When the answer should be "no": a {@link String}, a {@link java.util.Map} or a {@link java.util.List}, or
     * {@code null} if not set.
     */
    public Object noWhen() {
        return noWhen;
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
        private Object yesWhen;
        private Object noWhen;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Describes when the answer should be "yes".
         */
        public Builder yesWhen(String description) {
            return yesWhen((Object) description);
        }

        /**
         * Describes when the answer should be "yes", as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}.
         */
        public Builder yesWhen(Object description) {
            this.yesWhen = description;
            return this;
        }

        /**
         * Describes when the answer should be "no".
         */
        public Builder noWhen(String description) {
            return noWhen((Object) description);
        }

        /**
         * Describes when the answer should be "no", as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}.
         */
        public Builder noWhen(Object description) {
            this.noWhen = description;
            return this;
        }

        public YesNoQuestion build() {
            return new YesNoQuestion(this);
        }
    }
}
