package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.DecisionResponseMetadata;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The metadata of a {@link DecisionResponse} of a {@link GoogleAiGeminiDecisionModel}. In addition to the model name
 * and the token usage, it says why Gemini blocked the input, if it did. Then the answer to every question is a
 * {@link RefusalAnswer}. It also gives access to the raw HTTP response.
 * <p>
 * Gemini does not report the model in its response, so {@link #modelName()} is the model name of the request.
 * <pre>{@code
 * DecisionResponse response = decisionModel.decide(request);
 * GoogleAiGeminiDecisionResponseMetadata metadata = (GoogleAiGeminiDecisionResponseMetadata) response.metadata();
 * String blockReason = metadata.blockReason(); // for example "safety", or null when the input was not blocked
 * }</pre>
 *
 * @since 1.24.0
 */
@Experimental
public class GoogleAiGeminiDecisionResponseMetadata extends DecisionResponseMetadata {

    private final @Nullable String blockReason;
    private final @Nullable String blockMessage;
    private final @Nullable SuccessfulHttpResponse rawHttpResponse;

    private GoogleAiGeminiDecisionResponseMetadata(Builder builder) {
        super(builder);
        this.blockReason = builder.blockReason;
        this.blockMessage = builder.blockMessage;
        this.rawHttpResponse = builder.rawHttpResponse;
    }

    /**
     * Why Gemini blocked the input, as reported by Gemini, for example {@code "safety"} or
     * {@code "prohibited_content"}, or {@code null} when the input was not blocked. The value is in lower case, as the
     * Decisions API reports it, unlike {@link GoogleAiGeminiChatResponseMetadata#blockReason()}.
     */
    public @Nullable String blockReason() {
        return blockReason;
    }

    /**
     * Gemini's explanation of why it blocked the input, or {@code null} when the input was not blocked or Gemini gave
     * no explanation.
     */
    public @Nullable String blockMessage() {
        return blockMessage;
    }

    /**
     * The raw HTTP response of the Gemini Decisions API, for example to read a field that this class does not expose
     * yet, or {@code null} when Gemini rejected blocked input with an HTTP 400 error, which is not a successful
     * response.
     */
    public @Nullable SuccessfulHttpResponse rawHttpResponse() {
        return rawHttpResponse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        GoogleAiGeminiDecisionResponseMetadata that = (GoogleAiGeminiDecisionResponseMetadata) o;
        return Objects.equals(blockReason, that.blockReason)
                && Objects.equals(blockMessage, that.blockMessage)
                && Objects.equals(rawHttpResponse, that.rawHttpResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), blockReason, blockMessage, rawHttpResponse);
    }

    @Override
    public String toString() {
        return "GoogleAiGeminiDecisionResponseMetadata{modelName=" + modelName() + ", tokenUsage=" + tokenUsage()
                + ", blockReason=" + blockReason + ", blockMessage=" + blockMessage + ", rawHttpResponse="
                + rawHttpResponse + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DecisionResponseMetadata.Builder<Builder> {

        private @Nullable String blockReason;
        private @Nullable String blockMessage;
        private @Nullable SuccessfulHttpResponse rawHttpResponse;

        /**
         * Why Gemini blocked the input. {@code null} means that the input was not blocked.
         */
        public Builder blockReason(@Nullable String blockReason) {
            this.blockReason = blockReason;
            return this;
        }

        /**
         * Gemini's explanation of why it blocked the input. {@code null} means that there is none.
         */
        public Builder blockMessage(@Nullable String blockMessage) {
            this.blockMessage = blockMessage;
            return this;
        }

        /**
         * The raw HTTP response. {@code null} means that there is none, for example because Gemini rejected blocked
         * input with an HTTP 400 error.
         */
        public Builder rawHttpResponse(@Nullable SuccessfulHttpResponse rawHttpResponse) {
            this.rawHttpResponse = rawHttpResponse;
            return this;
        }

        @Override
        public GoogleAiGeminiDecisionResponseMetadata build() {
            return new GoogleAiGeminiDecisionResponseMetadata(this);
        }
    }
}
