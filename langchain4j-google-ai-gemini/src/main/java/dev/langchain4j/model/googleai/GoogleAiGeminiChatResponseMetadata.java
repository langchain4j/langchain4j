package dev.langchain4j.model.googleai;

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
    private final String blockReason;

    private GoogleAiGeminiChatResponseMetadata(Builder builder) {
        super(builder);
        this.groundingMetadata = builder.groundingMetadata;
        this.urlContextMetadata = builder.urlContextMetadata;
        this.safetyRatings = builder.safetyRatings;
        this.blockReason = builder.blockReason;
    }

    public GroundingMetadata groundingMetadata() {
        return groundingMetadata;
    }

    public UrlContextMetadata urlContextMetadata() {
        return urlContextMetadata;
    }

    /**
     * The safety assessments reported for this response, in the order returned by the API. Empty when the
     * response carries no safety ratings.
     *
     * @return an immutable view of the safety ratings, or an empty list when none are present
     */
    public List<GeminiSafetyRating> safetyRatings() {
        return safetyRatings;
    }

    /**
     * The reason the prompt was blocked, as reported by Gemini's {@code promptFeedback}, for example
     * {@code PROHIBITED_CONTENT} or {@code BLOCKLIST}. {@code null} when the prompt was not blocked.
     *
     * @return the prompt block reason, or {@code null} when the prompt was not blocked
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
                && Objects.equals(blockReason, that.blockReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groundingMetadata, urlContextMetadata, safetyRatings, blockReason);
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
                + safetyRatings + ", blockReason='"
                + blockReason + '\'' + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private GroundingMetadata groundingMetadata;
        private UrlContextMetadata urlContextMetadata;
        private List<GeminiSafetyRating> safetyRatings = List.of();
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
