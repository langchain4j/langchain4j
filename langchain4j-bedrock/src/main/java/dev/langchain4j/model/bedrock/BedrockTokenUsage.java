package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * Bedrock-specific token usage that includes cache-related metrics.
 * <p>
 * This class extends {@link TokenUsage} to include AWS Bedrock prompt caching
 * metrics: {@link #cacheWriteInputTokens()} and {@link #cacheReadInputTokens()}.
 *
 * @since 1.0.0-beta2
 */
public class BedrockTokenUsage extends TokenUsage {

    private final Integer cacheWriteInputTokens;
    private final Integer cacheReadInputTokens;

    public BedrockTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount);
        this.cacheWriteInputTokens = builder.cacheWriteInputTokens;
        this.cacheReadInputTokens = builder.cacheReadInputTokens;
    }

    /**
     * Returns The total cached token write count, or null if unknown.
     *
     * @return The total cached token write count, or null if unknown.
     */
    public Integer cacheWriteInputTokens() {
        return cacheWriteInputTokens;
    }

    /**
     * Returns The total cached token read count, or null if unknown.
     *
     * @return The total cached token read count, or null if unknown.
     */
    public Integer cacheReadInputTokens() {
        return cacheReadInputTokens;
    }

    @Override
    public BedrockTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }

        return builder()
                .inputTokenCount(sum(this.inputTokenCount(), that.inputTokenCount()))
                .outputTokenCount(sum(this.outputTokenCount(), that.outputTokenCount()))
                .cacheWriteInputTokens(addCacheWriteInputTokens(that))
                .cacheReadInputTokens(addCacheReadInputTokens(that))
                .build();
    }

    private Integer addCacheWriteInputTokens(TokenUsage that) {
        if (that instanceof BedrockTokenUsage thatBedrockTokenUsage) {
            return sum(this.cacheWriteInputTokens, thatBedrockTokenUsage.cacheWriteInputTokens);
        } else {
            return this.cacheWriteInputTokens;
        }
    }

    private Integer addCacheReadInputTokens(TokenUsage that) {
        if (that instanceof BedrockTokenUsage thatBedrockTokenUsage) {
            return sum(this.cacheReadInputTokens, thatBedrockTokenUsage.cacheReadInputTokens);
        } else {
            return this.cacheReadInputTokens;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BedrockTokenUsage that = (BedrockTokenUsage) o;
        return Objects.equals(cacheWriteInputTokens, that.cacheWriteInputTokens)
                && Objects.equals(cacheReadInputTokens, that.cacheReadInputTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cacheWriteInputTokens, cacheReadInputTokens);
    }

    @Override
    public String toString() {
        return "BedrockTokenUsage {" + " inputTokenCount = "
                + inputTokenCount() + ", outputTokenCount = "
                + outputTokenCount() + ", totalTokenCount = "
                + totalTokenCount() + ", cacheWriteInputTokens = "
                + cacheWriteInputTokens + ", cacheReadInputTokens = "
                + cacheReadInputTokens + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer inputTokenCount;
        private Integer outputTokenCount;
        private Integer cacheWriteInputTokens;
        private Integer cacheReadInputTokens;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        public Builder cacheWriteInputTokens(Integer cacheWriteInputTokens) {
            this.cacheWriteInputTokens = cacheWriteInputTokens;
            return this;
        }

        public Builder cacheReadInputTokens(Integer cacheReadInputTokens) {
            this.cacheReadInputTokens = cacheReadInputTokens;
            return this;
        }

        public BedrockTokenUsage build() {
            return new BedrockTokenUsage(this);
        }
    }
}
