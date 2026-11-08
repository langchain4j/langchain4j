package dev.langchain4j.model.openai;

import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * Token usage of an OpenAI image generation or edit request, as returned by {@link OpenAiImageModel}
 * in {@link dev.langchain4j.model.output.Response#tokenUsage()}.
 * <p>
 * In addition to the input, output and total token counts, it reports how many of the input and
 * output tokens were image tokens and how many were text tokens. OpenAI prices these differently,
 * so the split is needed to compute the cost of a request.
 * <p>
 * Only models that report usage (such as {@code gpt-image-1}) return it. For other models (such as
 * {@code dall-e-3}), {@code Response#tokenUsage()} is {@code null}.
 *
 * @since 1.21.0
 */
public class OpenAiImageTokenUsage extends TokenUsage {

    private final TokensDetails inputTokensDetails;
    private final TokensDetails outputTokensDetails;

    private OpenAiImageTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount, builder.totalTokenCount);
        this.inputTokensDetails = builder.inputTokensDetails;
        this.outputTokensDetails = builder.outputTokensDetails;
    }

    /**
     * Returns the breakdown of the input tokens into image and text tokens,
     * or {@code null} if OpenAI did not report it.
     */
    public TokensDetails inputTokensDetails() {
        return inputTokensDetails;
    }

    /**
     * Returns the breakdown of the output tokens into image and text tokens,
     * or {@code null} if OpenAI did not report it.
     */
    public TokensDetails outputTokensDetails() {
        return outputTokensDetails;
    }

    @Override
    public OpenAiImageTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }

        TokensDetails thatInputTokensDetails = null;
        TokensDetails thatOutputTokensDetails = null;
        if (that instanceof OpenAiImageTokenUsage thatImageTokenUsage) {
            thatInputTokensDetails = thatImageTokenUsage.inputTokensDetails;
            thatOutputTokensDetails = thatImageTokenUsage.outputTokensDetails;
        }

        return OpenAiImageTokenUsage.builder()
                .inputTokenCount(sum(this.inputTokenCount(), that.inputTokenCount()))
                .inputTokensDetails(TokensDetails.sum(this.inputTokensDetails, thatInputTokensDetails))
                .outputTokenCount(sum(this.outputTokenCount(), that.outputTokenCount()))
                .outputTokensDetails(TokensDetails.sum(this.outputTokensDetails, thatOutputTokensDetails))
                .totalTokenCount(sum(this.totalTokenCount(), that.totalTokenCount()))
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiImageTokenUsage that = (OpenAiImageTokenUsage) o;
        return Objects.equals(inputTokensDetails, that.inputTokensDetails)
                && Objects.equals(outputTokensDetails, that.outputTokensDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), inputTokensDetails, outputTokensDetails);
    }

    @Override
    public String toString() {
        return "OpenAiImageTokenUsage {" + " inputTokenCount = "
                + inputTokenCount() + ", inputTokensDetails = "
                + inputTokensDetails + ", outputTokenCount = "
                + outputTokenCount() + ", outputTokensDetails = "
                + outputTokensDetails + ", totalTokenCount = "
                + totalTokenCount() + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer inputTokenCount;
        private TokensDetails inputTokensDetails;
        private Integer outputTokenCount;
        private TokensDetails outputTokensDetails;
        private Integer totalTokenCount;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        public Builder inputTokensDetails(TokensDetails inputTokensDetails) {
            this.inputTokensDetails = inputTokensDetails;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        public Builder outputTokensDetails(TokensDetails outputTokensDetails) {
            this.outputTokensDetails = outputTokensDetails;
            return this;
        }

        public Builder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public OpenAiImageTokenUsage build() {
            return new OpenAiImageTokenUsage(this);
        }
    }

    /**
     * How many tokens were image tokens and how many were text tokens.
     */
    public static class TokensDetails {

        private final Integer imageTokens;
        private final Integer textTokens;

        public TokensDetails(Builder builder) {
            this.imageTokens = builder.imageTokens;
            this.textTokens = builder.textTokens;
        }

        /**
         * Returns the number of image tokens, or {@code null} if OpenAI did not report it.
         */
        public Integer imageTokens() {
            return imageTokens;
        }

        /**
         * Returns the number of text tokens, or {@code null} if OpenAI did not report it.
         */
        public Integer textTokens() {
            return textTokens;
        }

        private static TokensDetails sum(TokensDetails first, TokensDetails second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }
            return builder()
                    .imageTokens(TokenUsage.sum(first.imageTokens, second.imageTokens))
                    .textTokens(TokenUsage.sum(first.textTokens, second.textTokens))
                    .build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private Integer imageTokens;
            private Integer textTokens;

            public Builder imageTokens(Integer imageTokens) {
                this.imageTokens = imageTokens;
                return this;
            }

            public Builder textTokens(Integer textTokens) {
                this.textTokens = textTokens;
                return this;
            }

            public TokensDetails build() {
                return new TokensDetails(this);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (TokensDetails) obj;
            return Objects.equals(this.imageTokens, that.imageTokens)
                    && Objects.equals(this.textTokens, that.textTokens);
        }

        @Override
        public int hashCode() {
            return Objects.hash(imageTokens, textTokens);
        }

        @Override
        public String toString() {
            return "OpenAiImageTokenUsage.TokensDetails {" + " imageTokens = "
                    + imageTokens + ", textTokens = "
                    + textTokens + " }";
        }
    }
}
