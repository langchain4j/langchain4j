package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.output.TokenUsage;

public class AnthropicTokenUsage extends TokenUsage {

    private final Integer cacheCreationInputTokens;
    private final Integer cacheReadInputTokens;

    public AnthropicTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount);
        this.cacheCreationInputTokens = builder.cacheCreationInputTokens;
        this.cacheReadInputTokens = builder.cacheReadInputTokens;
    }

    /**
     * Creates a new {@link AnthropicTokenUsage} instance with the given input, output token counts
     * and cache creation/read input tokens.
     *
     * @param inputTokenCount          The input token count, or null if unknown.
     * @param outputTokenCount         The output token count, or null if unknown.
     * @param cacheCreationInputTokens The total cached token created count, or null if unknown.
     * @param cacheReadInputTokens     The total cached token read count, or null if unknown.
     * @deprecated please use {@link #builder()} instead
     */
    @Deprecated(forRemoval = true, since = "1.0.0-beta4")
    public AnthropicTokenUsage(Integer inputTokenCount,
                               Integer outputTokenCount,
                               Integer cacheCreationInputTokens,
                               Integer cacheReadInputTokens) {
        super(inputTokenCount, outputTokenCount);
        this.cacheCreationInputTokens = cacheCreationInputTokens;
        this.cacheReadInputTokens = cacheReadInputTokens;
    }

    /**
     * Returns The total cached token created count, or null if unknown.
     *
     * @return The total cached token created count, or null if unknown.
     */
    public Integer cacheCreationInputTokens() {
        return cacheCreationInputTokens;
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
    public AnthropicTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }

        return builder()
                .inputTokenCount(sum(this.inputTokenCount(), that.inputTokenCount()))
                .outputTokenCount(sum(this.outputTokenCount(), that.outputTokenCount()))
                .cacheCreationInputTokens(addCacheCreationInputTokens(that))
                .cacheReadInputTokens(addCacheReadInputTokens(that))
                .build();
    }

    private Integer addCacheCreationInputTokens(TokenUsage that) {
        if (that instanceof AnthropicTokenUsage thatAnthropicTokenUsage) {
            return sum(this.cacheCreationInputTokens, thatAnthropicTokenUsage.cacheCreationInputTokens);
        } else {
            return this.cacheCreationInputTokens;
        }
    }

    private Integer addCacheReadInputTokens(TokenUsage that) {
        if (that instanceof AnthropicTokenUsage thatAnthropicTokenUsage) {
            return sum(this.cacheReadInputTokens, thatAnthropicTokenUsage.cacheReadInputTokens);
        } else {
            return this.cacheReadInputTokens;
        }
    }

    @Override
    public String toString() {
        return "AnthropicTokenUsage {" +
                " inputTokenCount = " + inputTokenCount() +
                ", outputTokenCount = " + outputTokenCount() +
                ", totalTokenCount = " + totalTokenCount() +
                ", cacheCreationInputTokens = " + cacheCreationInputTokens +
                ", cacheReadInputTokens = " + cacheReadInputTokens +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer inputTokenCount;
        private Integer outputTokenCount;
        private Integer cacheCreationInputTokens;
        private Integer cacheReadInputTokens;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        public Builder cacheCreationInputTokens(Integer cacheCreationInputTokens) {
            this.cacheCreationInputTokens = cacheCreationInputTokens;
            return this;
        }

        public Builder cacheReadInputTokens(Integer cacheReadInputTokens) {
            this.cacheReadInputTokens = cacheReadInputTokens;
            return this;
        }

        public AnthropicTokenUsage build() {
            return new AnthropicTokenUsage(this);
        }
    }
}
