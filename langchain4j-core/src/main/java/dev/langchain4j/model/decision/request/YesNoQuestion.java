package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A yes/no question, answered with the probability that the answer is "yes"
 * (see {@link dev.langchain4j.model.decision.response.YesNoAnswer}).
 * <p>
 * Optionally, {@link #whenYes()} and {@link #whenNo()} describe when the answer should be "yes" and when it
 * should be "no". Each description is either plain text or structured content (a {@link java.util.Map} or a
 * {@link java.util.List}) that is passed to the model as is:
 * <pre>{@code
 * YesNoQuestion refundRequested = YesNoQuestion.builder()
 *         .instructions("Does the customer ask for a refund?")
 *         .whenYes("The customer explicitly asks for their money back")
 *         .whenNo("The customer only asks about a charge")
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class YesNoQuestion implements Question {

    private final String instructions;
    private final Object whenYes;
    private final Object whenNo;

    private YesNoQuestion(Builder builder) {
        this.instructions = ensureNotBlank(builder.instructions, "instructions");
        this.whenYes = builder.whenYes == null ? null : FreeFormValue.ensureValid(builder.whenYes, "whenYes");
        this.whenNo = builder.whenNo == null ? null : FreeFormValue.ensureValid(builder.whenNo, "whenNo");
    }

    @Override
    public String instructions() {
        return instructions;
    }

    /**
     * When the answer should be "yes": a {@link String}, a {@link java.util.Map} or a {@link java.util.List}, or
     * {@code null} if not set.
     */
    public Object whenYes() {
        return whenYes;
    }

    /**
     * When the answer should be "no": a {@link String}, a {@link java.util.Map} or a {@link java.util.List}, or
     * {@code null} if not set.
     */
    public Object whenNo() {
        return whenNo;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YesNoQuestion that)) return false;
        return Objects.equals(instructions, that.instructions)
                && Objects.equals(whenYes, that.whenYes)
                && Objects.equals(whenNo, that.whenNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instructions, whenYes, whenNo);
    }

    @Override
    public String toString() {
        return "YesNoQuestion{instructions=" + instructions + ", whenYes=" + whenYes + ", whenNo=" + whenNo
                + '}';
    }

    public static final class Builder {

        private String instructions;
        private Object whenYes;
        private Object whenNo;

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Describes when the answer should be "yes".
         */
        public Builder whenYes(String description) {
            return whenYes((Object) description);
        }

        /**
         * Describes when the answer should be "yes", as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}.
         */
        public Builder whenYes(Object description) {
            this.whenYes = description;
            return this;
        }

        /**
         * Describes when the answer should be "no".
         */
        public Builder whenNo(String description) {
            return whenNo((Object) description);
        }

        /**
         * Describes when the answer should be "no", as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}.
         */
        public Builder whenNo(Object description) {
            this.whenNo = description;
            return this;
        }

        public YesNoQuestion build() {
            return new YesNoQuestion(this);
        }
    }
}
