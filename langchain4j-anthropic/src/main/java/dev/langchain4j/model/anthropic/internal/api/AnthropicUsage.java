package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

/**
 * Represents token usage statistics from an Anthropic API response.
 * <p>
 * Tracks input and output token counts, including cache-related token usage
 * for prompt caching features.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicUsage {

    /**
     * The number of input tokens consumed.
     */
    public Integer inputTokens;

    /**
     * The number of output tokens generated.
     */
    public Integer outputTokens;

    /**
     * The number of tokens used to create cache entries.
     */
    public Integer cacheCreationInputTokens;

    /**
     * The number of tokens read from cache.
     */
    public Integer cacheReadInputTokens;

    @Override
    public int hashCode() {
        return Objects.hash(inputTokens, outputTokens, cacheCreationInputTokens, cacheReadInputTokens);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnthropicUsage)) return false;
        AnthropicUsage that = (AnthropicUsage) obj;
        return Objects.equals(inputTokens, that.inputTokens)
                && Objects.equals(outputTokens, that.outputTokens)
                && Objects.equals(cacheCreationInputTokens, that.cacheCreationInputTokens)
                && Objects.equals(cacheReadInputTokens, that.cacheReadInputTokens);
    }

    @Override
    public String toString() {
        return "AnthropicUsage{" + "inputTokens="
                + inputTokens + ", outputTokens="
                + outputTokens + ", cacheCreationInputTokens="
                + cacheCreationInputTokens + ", cacheReadInputTokens="
                + cacheReadInputTokens + '}';
    }
}
