package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.copy;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.List;
import java.util.Objects;

/**
 * Gemini-specific metadata for {@link dev.langchain4j.model.chat.response.ChatResponse}.
 */
public class GoogleAiGeminiChatResponseMetadata extends ChatResponseMetadata {

    private final GroundingMetadata groundingMetadata;
    private final UrlContextMetadata urlContextMetadata;
    private final List<GeminiSafetyRating> safetyRatings;
    private final List<GeminiSafetyRating> promptSafetyRatings;
    private final String blockReason;

    private GoogleAiGeminiChatResponseMetadata(Builder builder) {
        super(builder);
        this.groundingMetadata = builder.groundingMetadata;
        this.urlContextMetadata = builder.urlContextMetadata;
        this.safetyRatings = copy(builder.safetyRatings);
        this.promptSafetyRatings = copy(builder.promptSafetyRatings);
        this.blockReason = builder.blockReason;
    }

    public GroundingMetadata groundingMetadata() {
        return groundingMetadata;
    }

    public UrlContextMetadata urlContextMetadata() {
        return urlContextMetadata;
    }

    /**
     * The safety assessments of the <em>generated content</em>, one per harm category that Gemini evaluated.
     *
     * <p>Use these to find out why generation stopped when {@link #finishReason()} is
     * {@link dev.langchain4j.model.output.FinishReason#CONTENT_FILTER}. To inspect the safety of the
     * <em>prompt</em> instead, use {@link #promptSafetyRatings()}.
     *
     * @return an unmodifiable list of safety ratings, empty when the response carries none
     */
    public List<GeminiSafetyRating> safetyRatings() {
        return safetyRatings;
    }

    /**
     * The safety assessments of the <em>prompt</em>, as reported by Gemini's {@code promptFeedback}.
     *
     * <p>These describe the request rather than the generated content, and are typically populated when Gemini
     * refuses to answer at all. In that case {@link #blockReason()} is set and {@link #safetyRatings()} is empty.
     *
     * @return an unmodifiable list of safety ratings for the prompt, empty when the response carries none
     */
    public List<GeminiSafetyRating> promptSafetyRatings() {
        return promptSafetyRatings;
    }

    /**
     * The reason Gemini refused to process the prompt, for example {@code "SAFETY"}, {@code "PROHIBITED_CONTENT"},
     * {@code "BLOCKLIST"} or {@code "IMAGE_SAFETY"}.
     *
     * <p>When this is set, Gemini returned no content at all: the {@link dev.langchain4j.data.message.AiMessage}
     * carries no text, {@link #finishReason()} is
     * {@link dev.langchain4j.model.output.FinishReason#CONTENT_FILTER}, and {@link #promptSafetyRatings()} explains
     * which harm categories were involved.
     *
     * @return the block reason, or {@code null} when the prompt was not blocked
     */
    public String blockReason() {
        return blockReason;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .groundingMetadata(groundingMetadata)
                .urlContextMetadata(urlContextMetadata)
                .safetyRatings(safetyRatings)
                .promptSafetyRatings(promptSafetyRatings)
                .blockReason(blockReason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoogleAiGeminiChatResponseMetadata that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(groundingMetadata, that.groundingMetadata)
                && Objects.equals(urlContextMetadata, that.urlContextMetadata)
                && Objects.equals(safetyRatings, that.safetyRatings)
                && Objects.equals(promptSafetyRatings, that.promptSafetyRatings)
                && Objects.equals(blockReason, that.blockReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                groundingMetadata,
                urlContextMetadata,
                safetyRatings,
                promptSafetyRatings,
                blockReason);
    }

    @Override
    public String toString() {
        return "GoogleAiGeminiChatResponseMetadata{" + "id='"
                + id() + '\'' + ", modelName='"
                + modelName() + '\'' + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + ", groundingMetadata="
                + groundingMetadata + ", urlContextMetadata="
                + urlContextMetadata + ", safetyRatings="
                + safetyRatings + ", promptSafetyRatings="
                + promptSafetyRatings + ", blockReason='"
                + blockReason + '\'' + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private GroundingMetadata groundingMetadata;
        private UrlContextMetadata urlContextMetadata;
        private List<GeminiSafetyRating> safetyRatings;
        private List<GeminiSafetyRating> promptSafetyRatings;
        private String blockReason;

        public Builder groundingMetadata(GroundingMetadata groundingMetadata) {
            this.groundingMetadata = groundingMetadata;
            return this;
        }

        public Builder urlContextMetadata(UrlContextMetadata urlContextMetadata) {
            this.urlContextMetadata = urlContextMetadata;
            return this;
        }

        public Builder safetyRatings(List<GeminiSafetyRating> safetyRatings) {
            this.safetyRatings = safetyRatings;
            return this;
        }

        public Builder promptSafetyRatings(List<GeminiSafetyRating> promptSafetyRatings) {
            this.promptSafetyRatings = promptSafetyRatings;
            return this;
        }

        public Builder blockReason(String blockReason) {
            this.blockReason = blockReason;
            return this;
        }

        @Override
        public GoogleAiGeminiChatResponseMetadata build() {
            return new GoogleAiGeminiChatResponseMetadata(this);
        }
    }
}
