package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A request to a {@link dev.langchain4j.model.decision.DecisionModel}: the {@link #input()} to evaluate and the
 * named {@link #questions()} to answer about it.
 * <p>
 * The input is one of:
 * <ul>
 *     <li>plain text: {@link Builder#input(String)};</li>
 *     <li>named fields, for example a support ticket together with the customer's plan:
 *     {@link Builder#inputFields(Map)};</li>
 *     <li>a list of contents, for example text and images: {@link Builder#inputContents(List)};</li>
 *     <li>a conversation, for example a chat between a user and an AI assistant:
 *     {@link Builder#inputMessages(List)}.</li>
 * </ul>
 * Not every decision model supports every kind of input: a model that does not support it throws an
 * {@link dev.langchain4j.exception.UnsupportedFeatureException}.
 * Every question is answered against the same input, and each answer is returned under the name of its question:
 * <pre>{@code
 * DecisionRequest request = DecisionRequest.builder()
 *         .inputFields(Map.of("ticket", "My payouts have been failing for 3 days", "plan", "enterprise"))
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

    private final DecisionInput input;
    private final Map<String, Question> questions;
    private final DecisionRequestParameters parameters;

    private DecisionRequest(Builder builder) {
        this.input = ensureNotNull(
                builder.input,
                "%s must be set with input(String), inputFields(Map), inputContents(List), inputMessages(List) "
                        + "or input(DecisionInput)",
                "input");
        this.questions = copy(ensureNotEmpty(builder.questions, "questions"));
        this.parameters = getOrDefault(builder.parameters, DecisionRequestParameters.EMPTY);
    }

    /**
     * The input to evaluate. Its {@link DecisionInput#type() type} tells which implementation it is.
     */
    public DecisionInput input() {
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

        private DecisionInput input;
        private final Map<String, Question> questions = new LinkedHashMap<>();
        private DecisionRequestParameters parameters;

        /**
         * Sets the input to evaluate as text. Replaces any input set before.
         */
        public Builder input(String text) {
            return input(TextInput.from(text));
        }

        /**
         * Sets the input to evaluate as named fields, for example
         * {@code Map.of("ticket", ticket, "plan", "enterprise")}. See {@link FieldsInput} for the supported values.
         * Replaces any input set before.
         *
         * @since 1.23.0
         */
        public Builder inputFields(Map<String, ?> fields) {
            return input(FieldsInput.from(fields));
        }

        /**
         * Sets the input to evaluate as a list of contents, for example a
         * {@link dev.langchain4j.data.message.TextContent} and an {@link dev.langchain4j.data.message.ImageContent}.
         * Which kinds of contents are supported depends on the decision model. Replaces any input set before.
         *
         * @since 1.23.0
         */
        public Builder inputContents(List<? extends Content> contents) {
            return input(ContentsInput.from(contents));
        }

        /**
         * Sets the input to evaluate as contents, see {@link #inputContents(List)}.
         *
         * @since 1.23.0
         */
        public Builder inputContents(Content... contents) {
            return inputContents(contents == null ? null : Arrays.asList(contents));
        }

        /**
         * Sets the input to evaluate as a conversation: messages in the order they were exchanged, for example a chat
         * between a user and an AI assistant, including the tools the assistant called
         * ({@link dev.langchain4j.data.message.AiMessage#toolExecutionRequests()}) and their results
         * ({@link dev.langchain4j.data.message.ToolExecutionResultMessage}). This lets you ask questions about the
         * whole conversation, for example whether the assistant answered the user's question, or whether a tool call
         * is safe to execute. Messages and tool results can contain text written to influence the decision (prompt
         * injection): do not rely on a decision alone to allow a dangerous action. Which kinds of messages and contents
         * are supported depends on the decision model. Replaces any input set before.
         *
         * @since 1.23.0
         */
        public Builder inputMessages(List<? extends ChatMessage> messages) {
            return input(MessagesInput.from(messages));
        }

        /**
         * Sets the input to evaluate as a conversation, see {@link #inputMessages(List)}.
         *
         * @since 1.23.0
         */
        public Builder inputMessages(ChatMessage... messages) {
            return inputMessages(messages == null ? null : Arrays.asList(messages));
        }

        /**
         * Sets the input to evaluate. Replaces any input set before.
         *
         * @since 1.23.0
         */
        public Builder input(DecisionInput input) {
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
