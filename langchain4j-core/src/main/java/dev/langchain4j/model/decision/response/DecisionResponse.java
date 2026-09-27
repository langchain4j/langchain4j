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
 * ChoiceAnswer team = (ChoiceAnswer) response.answers().get("team");
 * NoulAnswer urgent = (NoulAnswer) response.answers().get("urgent");
 *
 * if (urgent.probability() > 0.8) {
 *     escalate(team.choice());
 * }
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionResponse {

    private final Map<String, DecisionAnswer> answers;
    private final DecisionResponseMetadata metadata;

    protected DecisionResponse(Builder builder) {
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

    public DecisionResponseMetadata metadata() {
        return metadata;
    }

    public String modelName() {
        return metadata.modelName();
    }

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

    public static class Builder {

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
