package dev.langchain4j.model.decision.response;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The response of a {@link dev.langchain4j.model.decision.DecisionModel}: one {@link DecisionAnswer} per question
 * of the request, keyed by question name, together with the {@link DecisionResponseMetadata} (model name, token
 * usage).
 * <pre>{@code
 * DecisionResponse response = decisionModel.decide(request);
 *
 * ChoiceAnswer team = response.choice("team");
 * YesNoAnswer urgent = response.yesNo("urgent");
 *
 * if (urgent.probability() > 0.8) {
 *     escalate(team.value());
 * }
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class DecisionResponse {

    private final Map<String, DecisionAnswer> answers;
    private final DecisionResponseMetadata metadata;

    private DecisionResponse(Builder builder) {
        this.answers = copy(ensureNotEmpty(builder.answers, "answers"));
        DecisionResponseMetadata.Builder<?> metadataBuilder = DecisionResponseMetadata.builder();
        if (builder.modelName != null) {
            validate(builder, "modelName");
            metadataBuilder.modelName(builder.modelName);
        }
        if (builder.tokenUsage != null) {
            validate(builder, "tokenUsage");
            metadataBuilder.tokenUsage(builder.tokenUsage);
        }
        this.metadata = builder.metadata != null ? builder.metadata : metadataBuilder.build();
    }

    /**
     * The answers, keyed by the names of the questions they answer.
     */
    public Map<String, DecisionAnswer> answers() {
        return answers;
    }

    /**
     * Returns the answer to the {@link dev.langchain4j.model.decision.request.YesNoQuestion} with the given name.
     *
     * @throws IllegalArgumentException if there is no answer with this name, or it is not a {@link YesNoAnswer}.
     */
    public YesNoAnswer yesNo(String name) {
        return answer(name, YesNoAnswer.class);
    }

    /**
     * Returns the answer to the {@link dev.langchain4j.model.decision.request.ChoiceQuestion} with the given name.
     *
     * @throws IllegalArgumentException if there is no answer with this name, or it is not a {@link ChoiceAnswer}.
     */
    public ChoiceAnswer choice(String name) {
        return answer(name, ChoiceAnswer.class);
    }

    /**
     * Returns the answer to the {@link dev.langchain4j.model.decision.request.ScaleQuestion} with the given name.
     *
     * @throws IllegalArgumentException if there is no answer with this name, or it is not a {@link ScaleAnswer}.
     */
    public ScaleAnswer scale(String name) {
        return answer(name, ScaleAnswer.class);
    }

    /**
     * Returns the answer with the given name, as the given answer type. Useful for answer types defined by
     * integrations.
     *
     * @throws IllegalArgumentException if there is no answer with this name, or it is not of the given type.
     */
    public <A extends DecisionAnswer> A answer(String name, Class<A> type) {
        DecisionAnswer answer = answers.get(name);
        if (answer == null) {
            throw new IllegalArgumentException("There is no answer to a question named '%s'. Available answers: %s"
                    .formatted(name, answers.keySet()));
        }
        if (!type.isInstance(answer)) {
            throw new IllegalArgumentException("The answer to the question '%s' is a %s, not a %s"
                    .formatted(
                            name, answer.getClass().getSimpleName(), type.getSimpleName()));
        }
        return type.cast(answer);
    }

    public DecisionResponseMetadata metadata() {
        return metadata;
    }

    /**
     * The name of the model that produced the answers, or {@code null} if the model did not report it.
     */
    public String modelName() {
        return metadata.modelName();
    }

    /**
     * The token usage of the call, or {@code null} if the model did not report it.
     */
    public TokenUsage tokenUsage() {
        return metadata.tokenUsage();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DecisionResponse that = (DecisionResponse) o;
        return Objects.equals(answers, that.answers) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(answers, metadata);
    }

    @Override
    public String toString() {
        return "DecisionResponse{answers=" + answers + ", metadata=" + metadata + '}';
    }

    public static final class Builder {

        private final Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
        private DecisionResponseMetadata metadata;
        private String modelName;
        private TokenUsage tokenUsage;

        /**
         * Replaces all answers.
         */
        public Builder answers(Map<String, ? extends DecisionAnswer> answers) {
            this.answers.clear();
            if (answers != null) {
                answers.forEach(this::answer);
            }
            return this;
        }

        /**
         * Adds the answer to the question with the given name.
         */
        public Builder answer(String name, DecisionAnswer answer) {
            answers.put(ensureNotBlank(name, "answer name"), ensureNotNull(answer, "answer"));
            return this;
        }

        public Builder metadata(DecisionResponseMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public DecisionResponse build() {
            return new DecisionResponse(this);
        }
    }

    private static void validate(Builder builder, String name) {
        if (builder.metadata != null) {
            throw new IllegalArgumentException(
                    "Cannot set both 'metadata' and '%s' on DecisionResponse".formatted(name));
        }
    }
}
