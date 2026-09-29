package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A question that selects exactly one option out of a named set, answered with the chosen option and the
 * probability of each option (see {@link dev.langchain4j.model.decision.response.ChoiceAnswer}).
 * <p>
 * Each option has a name and, optionally, a description of when it applies. The description is either plain text or
 * structured content (a {@link java.util.Map} or a {@link java.util.List}) that is passed to the model as is.
 * Options whose name says it all, such as sentiments, need no description
 * ({@code ChoiceQuestion.of("What is the sentiment?", List.of("positive", "negative", "neutral"))}):
 * <pre>{@code
 * ChoiceQuestion team = ChoiceQuestion.builder()
 *         .text("Which team should handle this ticket?")
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

    private final String text;
    private final Map<String, Object> options;

    private ChoiceQuestion(Builder builder) {
        this.text = ensureNotBlank(builder.text, "text");
        ensureTrue(builder.options.size() >= 2, "ChoiceQuestion requires at least 2 options");
        this.options = copy(builder.options);
    }

    @Override
    public String text() {
        return text;
    }

    /**
     * The options, keyed by name, in the order they were added. Each value is a {@link String}, a
     * {@link java.util.Map}, a {@link java.util.List}, or {@code null} for an option without a description.
     */
    public Map<String, Object> options() {
        return options;
    }

    /**
     * Creates a choice question with the given text and options, keyed by option name. Each description is a
     * {@link String}, a {@link java.util.Map} or a {@link java.util.List}.
     */
    public static ChoiceQuestion of(String text, Map<String, ?> options) {
        return builder().text(text).options(options).build();
    }

    /**
     * Creates a choice question with the given text and options without descriptions, for options whose name says it
     * all.
     */
    public static ChoiceQuestion of(String text, List<String> options) {
        Builder builder = builder().text(text);
        if (options != null) {
            options.forEach(builder::option);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChoiceQuestion that)) return false;
        return Objects.equals(text, that.text) && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, options);
    }

    @Override
    public String toString() {
        return "ChoiceQuestion{text=" + text + ", options=" + options + '}';
    }

    public static final class Builder {

        private String text;
        private final Map<String, Object> options = new LinkedHashMap<>();

        public Builder text(String text) {
            this.text = text;
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
         * Adds an option without a description, for an option whose name says it all. Option names must be unique.
         */
        public Builder option(String name) {
            return addOption(name, null);
        }

        /**
         * Adds an option with a description of when it applies, as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}. Option names must be unique.
         */
        public Builder option(String name, Object description) {
            return addOption(name, FreeFormValue.ensureValid(description, "description"));
        }

        private Builder addOption(String name, Object description) {
            if (options.containsKey(ensureNotBlank(name, "option name"))) {
                throw new IllegalArgumentException("Option '%s' is already defined".formatted(name));
            }
            options.put(name, description);
            return this;
        }

        public ChoiceQuestion build() {
            return new ChoiceQuestion(this);
        }
    }
}
