package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A request to a {@link dev.langchain4j.model.decision.DecisionModel}: the {@link #input()} to evaluate and the
 * named {@link #questions()} to answer about it.
 * <p>
 * The input is plain text, a {@link java.util.Map} of named values (strings, numbers, booleans, maps and lists), for
 * example a support ticket together with the customer's plan, or a list of {@link Content}s, for example text and
 * images. Not every decision model supports every kind of input: a model that does not support it throws an
 * {@link dev.langchain4j.exception.UnsupportedFeatureException}.
 * Every question is answered against the same input, and each answer is returned under the name of its question:
 * <pre>{@code
 * DecisionRequest request = DecisionRequest.builder()
 *         .input(Map.of("ticket", "My payouts have been failing for 3 days", "plan", "enterprise"))
 *         .question("team", ChoiceQuestion.builder()
 *                 .text("Which team should handle this ticket?")
 *                 .option("billing", "Payments, invoices, refunds")
 *                 .option("support", "Problems using the product")
 *                 .build())
 *         .question("urgent", YesNoQuestion.builder()
 *                 .text("Does this need attention today?")
 *                 .build())
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class DecisionRequest {

    private final Object input;
    private final Map<String, Question> questions;
    private final DecisionRequestParameters parameters;

    private DecisionRequest(Builder builder) {
        this.input = FreeFormValue.ensureValid(builder.input, "input");
        this.questions = copy(ensureNotEmpty(builder.questions, "questions"));
        this.parameters = getOrDefault(builder.parameters, DecisionRequestParameters.EMPTY);
    }

    /**
     * The input to evaluate: a {@link String}, an unmodifiable {@link java.util.Map} with {@link String} keys, or an
     * unmodifiable {@link List} of {@link Content}s. A value of the map can be a {@link Content} or a list of
     * {@link Content}s (for example, a named image): implementations must not convert such values to JSON text, but
     * send them as contents or throw an {@link dev.langchain4j.exception.UnsupportedFeatureException}.
     */
    public Object input() {
        return input;
    }

    /**
     * The questions to answer, keyed by name, in the order they were added.
     */
    public Map<String, Question> questions() {
        return questions;
    }

    public DecisionRequestParameters parameters() {
        return parameters;
    }

    public String modelName() {
        return parameters.modelName();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with the input, the questions and the parameters of this request.
     */
    public Builder toBuilder() {
        Builder builder = new Builder().questions(questions).parameters(parameters);
        builder.input = input;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DecisionRequest that = (DecisionRequest) o;
        return Objects.equals(input, that.input)
                && Objects.equals(questions, that.questions)
                && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(input, questions, parameters);
    }

    @Override
    public String toString() {
        // the input is left out on purpose: it can contain personal data
        return "DecisionRequest{questions=" + questions + ", parameters=" + parameters + '}';
    }

    public static final class Builder {

        private Object input;
        private final Map<String, Question> questions = new LinkedHashMap<>();
        private DecisionRequestParameters parameters;

        /**
         * Sets the input to evaluate as text.
         */
        public Builder input(String input) {
            this.input = input;
            return this;
        }

        /**
         * Sets the input to evaluate as named values: strings, numbers, booleans, {@code null}s, maps and lists.
         * A value of the map can also be a {@link Content} or a list of {@link Content}s, for example a named image
         * ({@code Map.of("comment", comment, "photo", ImageContent.from(...))}); which kinds of contents are supported
         * depends on the decision model.
         * Other objects are rejected: convert them to a {@link java.util.Map} that holds only what the decision needs.
         */
        public Builder input(Map<String, ?> input) {
            this.input = input;
            return this;
        }

        /**
         * Sets the input to evaluate as a list of contents, for example a {@link dev.langchain4j.data.message.TextContent}
         * and an {@link dev.langchain4j.data.message.ImageContent}. Which kinds of contents are supported depends on the
         * decision model.
         *
         * @since 1.22.0
         */
        public Builder input(List<? extends Content> input) {
            this.input = input;
            return this;
        }

        /**
         * Replaces all questions.
         */
        public Builder questions(Map<String, ? extends Question> questions) {
            this.questions.clear();
            if (questions != null) {
                questions.forEach(this::question);
            }
            return this;
        }

        /**
         * Adds a question. Its answer is returned under the same name, so question names must be unique.
         */
        public Builder question(String name, Question question) {
            if (questions.containsKey(ensureNotBlank(name, "question name"))) {
                throw new IllegalArgumentException("Question '%s' is already defined".formatted(name));
            }
            questions.put(name, ensureNotNull(question, "question"));
            return this;
        }

        public Builder parameters(DecisionRequestParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public DecisionRequest build() {
            return new DecisionRequest(this);
        }
    }
}
