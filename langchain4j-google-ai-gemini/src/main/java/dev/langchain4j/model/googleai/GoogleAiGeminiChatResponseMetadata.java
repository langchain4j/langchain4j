package dev.langchain4j.model.googleai;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.Objects;

/**
 * Gemini-specific metadata for {@link dev.langchain4j.model.chat.response.ChatResponse}.
 */
public class GoogleAiGeminiChatResponseMetadata extends ChatResponseMetadata {

    private final GroundingMetadata groundingMetadata;
    private final UrlContextMetadata urlContextMetadata;

    private GoogleAiGeminiChatResponseMetadata(Builder builder) {
        super(builder);
        this.groundingMetadata = builder.groundingMetadata;
        this.urlContextMetadata = builder.urlContextMetadata;
    }

    public GroundingMetadata groundingMetadata() {
        return groundingMetadata;
    }

    public UrlContextMetadata urlContextMetadata() {
        return urlContextMetadata;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .groundingMetadata(groundingMetadata)
                .urlContextMetadata(urlContextMetadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoogleAiGeminiChatResponseMetadata that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(groundingMetadata, that.groundingMetadata)
                && Objects.equals(urlContextMetadata, that.urlContextMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groundingMetadata, urlContextMetadata);
    }

    @Override
    public String toString() {
        return "GoogleAiGeminiChatResponseMetadata{" + "id='"
                + id() + '\'' + ", modelName='"
                + modelName() + '\'' + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + ", groundingMetadata="
                + groundingMetadata + ", urlContextMetadata="
                + urlContextMetadata + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private GroundingMetadata groundingMetadata;
        private UrlContextMetadata urlContextMetadata;

        public Builder groundingMetadata(GroundingMetadata groundingMetadata) {
            this.groundingMetadata = groundingMetadata;
            return this;
        }

        public Builder urlContextMetadata(UrlContextMetadata urlContextMetadata) {
            this.urlContextMetadata = urlContextMetadata;
            return this;
        }

        @Override
        public GoogleAiGeminiChatResponseMetadata build() {
            return new GoogleAiGeminiChatResponseMetadata(this);
        }
    }
}
