package dev.langchain4j.model.google.genai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.DecisionResponseMetadata;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The metadata of a {@link DecisionResponse} of a {@link GoogleGenAiDecisionModel}. In addition to the model name and
 * the token usage, it reports why Gemini blocked the input, if it did. When the input is blocked, the answer to every
 * question is a {@link RefusalAnswer}.
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

    private GoogleGenAiDecisionResponseMetadata(Builder builder) {
        super(builder);
        this.blockReason = builder.blockReason;
        this.blockMessage = builder.blockMessage;
    }

    /**
     * Why Gemini blocked the input, as reported by Gemini, for example {@code "safety"} or
     * {@code "prohibited_content"}, or {@code null} when the input was not blocked.
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        GoogleGenAiDecisionResponseMetadata that = (GoogleGenAiDecisionResponseMetadata) o;
        return Objects.equals(blockReason, that.blockReason) && Objects.equals(blockMessage, that.blockMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), blockReason, blockMessage);
    }

    @Override
    public String toString() {
        return "GoogleGenAiDecisionResponseMetadata{modelName=" + modelName() + ", tokenUsage=" + tokenUsage()
                + ", blockReason=" + blockReason + ", blockMessage=" + blockMessage + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DecisionResponseMetadata.Builder<Builder> {

        private @Nullable String blockReason;
        private @Nullable String blockMessage;

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

        @Override
        public GoogleGenAiDecisionResponseMetadata build() {
            return new GoogleGenAiDecisionResponseMetadata(this);
        }
    }
}
