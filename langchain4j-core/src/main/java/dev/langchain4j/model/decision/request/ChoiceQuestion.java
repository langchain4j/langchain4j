package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A question that selects exactly one option out of a named set, answered with the chosen option and the
 * probability of each option (see {@link dev.langchain4j.model.decision.response.ChoiceAnswer}).
 * <p>
 * Each option has a name and a description of when it applies. The description is either plain text or structured
 * content (a {@link java.util.Map} or a {@link java.util.List}) that is passed to the model as is:
 * <pre>{@code
 * ChoiceQuestion team = ChoiceQuestion.builder()
 *         .instructions("Which team should handle this ticket?")
 *         .option("billing", "Payments, invoices, refunds")
 *         .option("support", Map.of(
 *                 "what", "Problems using the product",
 *                 "not_for", "Questions about invoices"))
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class ChoiceQuestion implements Question {

    private final String instructions;
    private final Map<String, Object> options;

    private ChoiceQuestion(Builder builder) {
        this.instructions = ensureNotBlank(builder.instructions, "instructions");
        ensureTrue(builder.options.size() >= 2, "ChoiceQuestion requires at least 2 options");
        this.options = copy(builder.options);
    }

    @Override
    public String instructions() {
        return instructions;
    }

    /**
     * The options, keyed by name, in the order they were added. Each value is a {@link String}, a
     * {@link java.util.Map} or a {@link java.util.List}.
     */
    public Map<String, Object> options() {
        return options;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChoiceQuestion that)) return false;
        return Objects.equals(instructions, that.instructions) && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instructions, options);
    }

    @Override
    public String toString() {
        return "ChoiceQuestion{instructions=" + instructions + ", options=" + options + '}';
    }

    public static final class Builder {

        private String instructions;
        private final Map<String, Object> options = new LinkedHashMap<>();

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Replaces all options. Each value is a {@link String}, a {@link java.util.Map} or a {@link java.util.List}.
         */
        public Builder options(Map<String, ?> options) {
            this.options.clear();
            if (options != null) {
                options.forEach(this::option);
            }
            return this;
        }

        /**
         * Adds an option with a plain-text description of when it applies.
         */
        public Builder option(String name, String description) {
            return option(name, (Object) description);
        }

        /**
         * Adds an option with a description of when it applies, as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}.
         */
        public Builder option(String name, Object description) {
            options.put(ensureNotBlank(name, "option name"), FreeFormValue.ensureValid(description, "description"));
            return this;
        }

        public ChoiceQuestion build() {
            return new ChoiceQuestion(this);
        }
    }
}
