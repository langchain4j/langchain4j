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
 * Each option has a name and a description of when it applies. For options whose name says it all, such as
 * sentiments, the name is used as the description
 * ({@code ChoiceQuestion.of("What is the sentiment?", List.of("positive", "negative", "neutral"))}):
 * <pre>{@code
 * ChoiceQuestion team = ChoiceQuestion.builder()
 *         .text("Which team should handle this ticket?")
 *         .option("billing", "Payments, invoices, refunds")
 *         .option("support", "Problems using the product. Not for questions about invoices")
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class ChoiceQuestion implements Question {

    private final String text;
    private final Map<String, String> options;

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
     * The descriptions of the options, keyed by option name, in the order they were added. For an option added without
     * a description, the description is its name.
     */
    public Map<String, String> options() {
        return options;
    }

    /**
     * Creates a choice question with the given text and the descriptions of the options, keyed by option name.
     */
    public static ChoiceQuestion of(String text, Map<String, String> options) {
        return builder().text(text).options(options).build();
    }

    /**
     * Creates a choice question with the given text and options whose names say it all: each name is also used as
     * the description.
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
        private final Map<String, String> options = new LinkedHashMap<>();

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Replaces all options with the given descriptions, keyed by option name. A {@code null} description adds an
         * option whose name is used as the description (see {@link #option(String)}).
         */
        public Builder options(Map<String, String> options) {
            this.options.clear();
            if (options != null) {
                options.forEach((name, description) -> {
                    if (description == null) {
                        option(name);
                    } else {
                        option(name, description);
                    }
                });
            }
            return this;
        }

        /**
         * Adds an option whose name says it all: the name is also used as the description, since models decide with
         * more confidence when an option has a description. Option names must be unique.
         */
        public Builder option(String name) {
            return option(name, name);
        }

        /**
         * Adds an option with a description of when it applies. Option names must be unique.
         */
        public Builder option(String name, String description) {
            return addOption(name, ensureNotBlank(description, "description"));
        }

        private Builder addOption(String name, String description) {
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
