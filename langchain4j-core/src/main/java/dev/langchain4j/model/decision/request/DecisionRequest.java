package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A request to a {@link dev.langchain4j.model.decision.DecisionModel}: the {@link #state()} to evaluate and the
 * named {@link #questions()} to answer about it.
 * <p>
 * The state is either plain text or structured content (a {@link java.util.Map} or a {@link java.util.List}), for
 * example a support ticket together with the customer's plan. Every question is answered against the same state,
 * and each answer is returned under the name of its question:
 * <pre>{@code
 * DecisionRequest request = DecisionRequest.builder()
 *         .state(Map.of("ticket", "My payouts have been failing for 3 days", "plan", "enterprise"))
 *         .question("team", ChoiceQuestion.builder()
 *                 .instructions("Which team should handle this ticket?")
 *                 .option("billing", "Payments, invoices, refunds")
 *                 .option("support", "Problems using the product")
 *                 .build())
 *         .question("urgent", NoulQuestion.builder()
 *                 .instructions("Does this need attention today?")
 *                 .build())
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionRequest {

    private final Object state;
    private final Map<String, Question> questions;
    private final DecisionRequestParameters parameters;

    protected DecisionRequest(Builder builder) {
        this.state = FreeFormValue.ensureValid(builder.state, "state");
        this.questions = copy(ensureNotEmpty(builder.questions, "questions"));
        this.parameters = getOrDefault(builder.parameters, DecisionRequestParameters.EMPTY);
    }

    /**
     * The state to evaluate: a {@link String}, a {@link java.util.Map} or a {@link java.util.List}.
     */
    public Object state() {
        return state;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DecisionRequest that = (DecisionRequest) o;
        return Objects.equals(state, that.state)
                && Objects.equals(questions, that.questions)
                && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, questions, parameters);
    }

    @Override
    public String toString() {
        return "DecisionRequest{state=" + state + ", questions=" + questions + ", parameters=" + parameters + '}';
    }

    public static class Builder {

        private Object state;
        private final Map<String, Question> questions = new LinkedHashMap<>();
        private DecisionRequestParameters parameters;

        /**
         * Sets the state to evaluate, as a {@link String}, a {@link java.util.Map} or a {@link java.util.List}.
         */
        public Builder state(Object state) {
            this.state = state;
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
         * Adds a question. Its answer is returned under the same name.
         */
        public Builder question(String name, Question question) {
            questions.put(ensureNotBlank(name, "question name"), ensureNotNull(question, "question"));
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
