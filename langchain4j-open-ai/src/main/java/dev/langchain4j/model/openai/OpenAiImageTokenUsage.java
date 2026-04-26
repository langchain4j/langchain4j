package dev.langchain4j.model.openai;

import dev.langchain4j.model.output.TokenUsage;

import java.util.Objects;

/**
 * Token usage reported by OpenAI's gpt-image-* models. Extends the generic {@link TokenUsage}
 * with a per-direction text/image token breakdown returned in the {@code input_tokens_details}
 * and {@code output_tokens_details} fields of the response.
 *
 * <p>dall-e responses don't carry usage information, so {@link dev.langchain4j.model.output.Response#tokenUsage()}
 * is {@code null} on those paths.
 */
public class OpenAiImageTokenUsage extends TokenUsage {

    private final TokenDetails inputTokensDetails;
    private final TokenDetails outputTokensDetails;

    private OpenAiImageTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount, builder.totalTokenCount);
        this.inputTokensDetails = builder.inputTokensDetails;
        this.outputTokensDetails = builder.outputTokensDetails;
    }

    public TokenDetails inputTokensDetails() {
        return inputTokensDetails;
    }

    public TokenDetails outputTokensDetails() {
        return outputTokensDetails;
    }

    @Override
    public OpenAiImageTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }
        return OpenAiImageTokenUsage.builder()
                .inputTokenCount(addIntegers(this.inputTokenCount(), that.inputTokenCount()))
                .outputTokenCount(addIntegers(this.outputTokenCount(), that.outputTokenCount()))
                .totalTokenCount(addIntegers(this.totalTokenCount(), that.totalTokenCount()))
                .inputTokensDetails(addInputDetails(that))
                .outputTokensDetails(addOutputDetails(that))
                .build();
    }

    private TokenDetails addInputDetails(TokenUsage that) {
        if (that instanceof OpenAiImageTokenUsage other) {
            return TokenDetails.add(this.inputTokensDetails, other.inputTokensDetails);
        }
        return this.inputTokensDetails;
    }

    private TokenDetails addOutputDetails(TokenUsage that) {
        if (that instanceof OpenAiImageTokenUsage other) {
            return TokenDetails.add(this.outputTokensDetails, other.outputTokensDetails);
        }
        return this.outputTokensDetails;
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
        return "OpenAiImageTokenUsage {" +
                " inputTokenCount = " + inputTokenCount() +
                ", inputTokensDetails = " + inputTokensDetails +
                ", outputTokenCount = " + outputTokenCount() +
                ", outputTokensDetails = " + outputTokensDetails +
                ", totalTokenCount = " + totalTokenCount() +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer inputTokenCount;
        private Integer outputTokenCount;
        private Integer totalTokenCount;
        private TokenDetails inputTokensDetails;
        private TokenDetails outputTokensDetails;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        public Builder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public Builder inputTokensDetails(TokenDetails inputTokensDetails) {
            this.inputTokensDetails = inputTokensDetails;
            return this;
        }

        public Builder outputTokensDetails(TokenDetails outputTokensDetails) {
            this.outputTokensDetails = outputTokensDetails;
            return this;
        }

        public OpenAiImageTokenUsage build() {
            return new OpenAiImageTokenUsage(this);
        }
    }

    /**
     * Per-direction text/image token breakdown. Used for both {@code input_tokens_details}
     * and {@code output_tokens_details}, which share the same shape.
     */
    public static class TokenDetails {

        private final Integer textTokens;
        private final Integer imageTokens;

        public TokenDetails(Integer textTokens, Integer imageTokens) {
            this.textTokens = textTokens;
            this.imageTokens = imageTokens;
        }

        public Integer textTokens() {
            return textTokens;
        }

        public Integer imageTokens() {
            return imageTokens;
        }

        static TokenDetails add(TokenDetails a, TokenDetails b) {
            if (a == null) return b;
            if (b == null) return a;
            return new TokenDetails(addIntegers(a.textTokens, b.textTokens), addIntegers(a.imageTokens, b.imageTokens));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TokenDetails that = (TokenDetails) o;
            return Objects.equals(textTokens, that.textTokens) && Objects.equals(imageTokens, that.imageTokens);
        }

        @Override
        public int hashCode() {
            return Objects.hash(textTokens, imageTokens);
        }

        @Override
        public String toString() {
            return "OpenAiImageTokenUsage.TokenDetails {"
                    + " textTokens = " + textTokens
                    + ", imageTokens = " + imageTokens
                    + " }";
        }
    }

    private static Integer addIntegers(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }
}
