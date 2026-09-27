package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A yes/no question, answered with the probability that the answer is "yes"
 * (see {@link dev.langchain4j.model.decision.response.NoulAnswer}).
 * <p>
 * Optionally, {@link #whenTrue()} and {@link #whenFalse()} describe when the answer should be "yes" and when it
 * should be "no". Each description is either plain text or structured content (a {@link java.util.Map} or a
 * {@link java.util.List}) that is passed to the model as is:
 * <pre>{@code
 * NoulQuestion refundRequested = NoulQuestion.builder()
 *         .instructions("Does the customer ask for a refund?")
 *         .whenTrue("The customer explicitly asks for their money back")
 *         .whenFalse("The customer only asks about a charge")
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class NoulQuestion implements Question {

    private final String instructions;
    private final Object whenTrue;
    private final Object whenFalse;

    private NoulQuestion(Builder builder) {
        this.instructions = ensureNotBlank(builder.instructions, "instructions");
        this.whenTrue = builder.whenTrue == null ? null : FreeFormValue.ensureValid(builder.whenTrue, "whenTrue");
        this.whenFalse = builder.whenFalse == null ? null : FreeFormValue.ensureValid(builder.whenFalse, "whenFalse");
    }

    @Override
    public String instructions() {
        return instructions;
    }

    /**
     * When the answer should be "yes": a {@link String}, a {@link java.util.Map} or a {@link java.util.List}, or
     * {@code null} if not set.
     */
    public Object whenTrue() {
        return whenTrue;
    }

    /**
     * When the answer should be "no": a {@link String}, a {@link java.util.Map} or a {@link java.util.List}, or
     * {@code null} if not set.
     */
    public Object whenFalse() {
        return whenFalse;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NoulQuestion that)) return false;
        return Objects.equals(instructions, that.instructions)
                && Objects.equals(whenTrue, that.whenTrue)
                && Objects.equals(whenFalse, that.whenFalse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instructions, whenTrue, whenFalse);
    }

    @Override
    public String toString() {
        return "NoulQuestion{instructions=" + instructions + ", whenTrue=" + whenTrue + ", whenFalse=" + whenFalse
                + '}';
    }

    public static final class Builder {

        private String instructions;
        private Object whenTrue;
        private Object whenFalse;

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Describes when the answer should be "yes".
         */
        public Builder whenTrue(String description) {
            return whenTrue((Object) description);
        }

        /**
         * Describes when the answer should be "yes", as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}.
         */
        public Builder whenTrue(Object criteria) {
            this.whenTrue = criteria;
            return this;
        }

        /**
         * Describes when the answer should be "no".
         */
        public Builder whenFalse(String description) {
            return whenFalse((Object) description);
        }

        /**
         * Describes when the answer should be "no", as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}.
         */
        public Builder whenFalse(Object criteria) {
            this.whenFalse = criteria;
            return this;
        }

        public NoulQuestion build() {
            return new NoulQuestion(this);
        }
    }
}
