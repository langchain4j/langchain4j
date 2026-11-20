package dev.langchain4j.model.google.genai;

import com.google.genai.gaos.models.decisions.CreateDecisionResponse;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.DecisionResponseMetadata;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The metadata of a {@link DecisionResponse} of a {@link GoogleGenAiDecisionModel}. In addition to the model name and
 * the token usage, it reports why Gemini blocked the input, if it did. When the input is blocked, the answer to every
 * question is a {@link RefusalAnswer}. It also gives access to the raw {@link CreateDecisionResponse} of the Google Gen
 * AI SDK.
 * <p>
 * Gemini does not report the model in its response, so {@link #modelName()} is the model name of the request.
 * <pre>{@code
 * DecisionResponse response = decisionModel.decide(request);
 * GoogleGenAiDecisionResponseMetadata metadata = (GoogleGenAiDecisionResponseMetadata) response.metadata();
 * String blockReason = metadata.blockReason(); // for example "safety", or null when the input was not blocked
 * }</pre>
 *
 * @since 1.24.0
 */
@Experimental
public class GoogleGenAiDecisionResponseMetadata extends DecisionResponseMetadata {

    private final @Nullable String blockReason;
    private final @Nullable String blockMessage;
    private final @Nullable CreateDecisionResponse rawResponse;

    private GoogleGenAiDecisionResponseMetadata(Builder builder) {
        super(builder);
        this.blockReason = builder.blockReason;
        this.blockMessage = builder.blockMessage;
        this.rawResponse = builder.rawResponse;
    }

    /**
     * Why Gemini blocked the input, as reported by Gemini, for example {@code "safety"} or
     * {@code "prohibited_content"}, or {@code null} when the input was not blocked. The value is in lower case, as the
     * Decisions API reports it.
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
     * The raw {@link CreateDecisionResponse} of the Google Gen AI SDK, for example to read a field that this class does
     * not expose yet, or {@code null} when Gemini rejected blocked input with an HTTP 400 error, which is not a
     * successful response.
     */
    public @Nullable CreateDecisionResponse rawResponse() {
        return rawResponse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        GoogleGenAiDecisionResponseMetadata that = (GoogleGenAiDecisionResponseMetadata) o;
        return Objects.equals(blockReason, that.blockReason)
                && Objects.equals(blockMessage, that.blockMessage)
                && Objects.equals(rawResponse, that.rawResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), blockReason, blockMessage, rawResponse);
    }

    @Override
    public String toString() {
        return "GoogleGenAiDecisionResponseMetadata{modelName=" + modelName() + ", tokenUsage=" + tokenUsage()
                + ", blockReason=" + blockReason + ", blockMessage=" + blockMessage + ", rawResponse=" + rawResponse
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DecisionResponseMetadata.Builder<Builder> {

        private @Nullable String blockReason;
        private @Nullable String blockMessage;
        private @Nullable CreateDecisionResponse rawResponse;

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
         * The raw response of the Google Gen AI SDK. {@code null} means that there is none, for example because Gemini
         * rejected blocked input with an HTTP 400 error.
         */
        public Builder rawResponse(@Nullable CreateDecisionResponse rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        @Override
        public GoogleGenAiDecisionResponseMetadata build() {
            return new GoogleGenAiDecisionResponseMetadata(this);
        }
    }
}
