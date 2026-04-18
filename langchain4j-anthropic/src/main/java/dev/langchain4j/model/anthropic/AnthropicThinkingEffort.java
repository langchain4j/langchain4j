package dev.langchain4j.model.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Controls the depth of thinking when using adaptive thinking
 * ({@code thinkingType = "adaptive"}).
 * Acts as soft guidance — Claude may deviate based on the actual request complexity.
 *
 * @see <a href="https://platform.claude.com/docs/en/build-with-claude/adaptive-thinking">Adaptive thinking</a>
 * @see <a href="https://platform.claude.com/docs/en/build-with-claude/effort">Effort parameter</a>
 */
public enum AnthropicThinkingEffort {

    /**
     * Claude always thinks with no constraints on thinking depth.
     * Only supported on Claude Opus 4.6; requests using {@code MAX} on other models return an error.
     */
    @JsonProperty("max")
    MAX,

    /**
     * Claude always thinks. Provides deep reasoning on complex tasks.
     * This is the default when no effort level is specified.
     */
    @JsonProperty("high")
    HIGH,

    /**
     * Claude uses moderate thinking. May skip thinking for very simple queries.
     */
    @JsonProperty("medium")
    MEDIUM,

    /**
     * Claude minimises thinking. Skips thinking for simple tasks where speed matters most.
     */
    @JsonProperty("low")
    LOW;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
