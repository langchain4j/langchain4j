package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.output.TokenUsage;

public class AnthropicTokenUsage extends TokenUsage {

    private final Integer cacheCreationInputTokens;
    private final Integer cacheReadInputTokens;

    /**
     * Creates a new {@link AnthropicTokenUsage} instance with the given input, output token counts
     * and cache creation/read input tokens.
     *
     * @param inputTokenCount          The input token count, or null if unknown.
     * @param outputTokenCount         The output token count, or null if unknown.
     * @param cacheCreationInputTokens The total cached token created count, or null if unknown.
     * @param cacheReadInputTokens     The total cached token read count, or null if unknown.
     */
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
}
