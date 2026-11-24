package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * The result of a decision service method together with the underlying {@link DecisionResponse}, which gives access
 * to the raw answers (including probabilities), the model name and the token usage.
 * <pre>{@code
 * interface SupportDesk {
 *
 *     @Decide("Which team should handle this ticket?")
 *     DecisionResult<Team> route(String ticket);
 * }
 * }</pre>
 *
 * @param <T> the type of the content
 * @since 1.21.0
 */
@Experimental
public final class DecisionResult<T> {

    private final T content;
    private final DecisionResponse response;

    private DecisionResult(Builder<T> builder) {
        this.content = ensureNotNull(builder.content, "content");
        this.response = ensureNotNull(builder.response, "response");
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public T content() {
        return content;
    }

    /**
     * The response of the decision model. Answers are keyed by the name of the method (for a single question) or by
     * the names of the fields of the returned object.
     */
    public DecisionResponse response() {
        return response;
    }

    public String modelName() {
        return response.modelName();
    }

    public TokenUsage tokenUsage() {
        return response.tokenUsage();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DecisionResult<?> that)) return false;
        return Objects.equals(content, that.content) && Objects.equals(response, that.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, response);
    }

    @Override
    public String toString() {
        return "DecisionResult{content=" + content + ", response=" + response + '}';
    }

    public static final class Builder<T> {

        private T content;
        private DecisionResponse response;

        public Builder<T> content(T content) {
            this.content = content;
            return this;
        }

        public Builder<T> response(DecisionResponse response) {
            this.response = response;
            return this;
        }

        public DecisionResult<T> build() {
            return new DecisionResult<>(this);
        }
    }
}
