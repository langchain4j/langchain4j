package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

public class GoogleAiGeminiChatRequestParameters extends DefaultChatRequestParameters {

    public static final GoogleAiGeminiChatRequestParameters EMPTY =
            GoogleAiGeminiChatRequestParameters.builder().build();

    private final String aspectRatio;
    private final String imageSize;
    private final String cachedContentName;

    private GoogleAiGeminiChatRequestParameters(Builder builder) {
        super(builder);
        this.aspectRatio = builder.aspectRatio;
        this.imageSize = builder.imageSize;
        this.cachedContentName = builder.cachedContentName;
    }

    public String aspectRatio() {
        return aspectRatio;
    }

    public String imageSize() {
        return imageSize;
    }

    /**
     * The resource name of a previously created cache (e.g. {@code "cachedContents/abc123"}) to attach
     * to this specific request, overriding any global {@code cachedContentName} configured on the
     * {@link GoogleAiGeminiChatModel}/{@link GoogleAiGeminiStreamingChatModel}.
     */
    public String cachedContentName() {
        return cachedContentName;
    }

    @Override
    public GoogleAiGeminiChatRequestParameters overrideWith(ChatRequestParameters that) {
        return GoogleAiGeminiChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public GoogleAiGeminiChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return GoogleAiGeminiChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GoogleAiGeminiChatRequestParameters that = (GoogleAiGeminiChatRequestParameters) o;
        return Objects.equals(aspectRatio, that.aspectRatio)
                && Objects.equals(imageSize, that.imageSize)
                && Objects.equals(cachedContentName, that.cachedContentName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), aspectRatio, imageSize, cachedContentName);
    }

    @Override
    public String toString() {
        return "GoogleAiGeminiChatRequestParameters{" + "modelName="
                + quoted(modelName()) + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", aspectRatio="
                + quoted(aspectRatio) + ", imageSize="
                + quoted(imageSize) + ", cachedContentName="
                + quoted(cachedContentName) + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String aspectRatio;
        private String imageSize;
        private String cachedContentName;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof GoogleAiGeminiChatRequestParameters geminiParameters) {
                aspectRatio(getOrDefault(geminiParameters.aspectRatio(), aspectRatio));
                imageSize(getOrDefault(geminiParameters.imageSize(), imageSize));
                cachedContentName(getOrDefault(geminiParameters.cachedContentName(), cachedContentName));
            }
            return this;
        }

        public Builder aspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        public Builder imageAspectRatio(String imageAspectRatio) {
            return aspectRatio(imageAspectRatio);
        }

        public Builder imageSize(String imageSize) {
            this.imageSize = imageSize;
            return this;
        }

        public Builder cachedContentName(String cachedContentName) {
            this.cachedContentName = cachedContentName;
            return this;
        }

        @Override
        public GoogleAiGeminiChatRequestParameters build() {
            return new GoogleAiGeminiChatRequestParameters(this);
        }
    }
}
