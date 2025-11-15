package dev.langchain4j.model.anthropic;

/**
 * See more details <a href="https://docs.anthropic.com/claude/docs/models-overview">here</a>.
 */
public enum AnthropicChatModelName {
    CLAUDE_HAIKU_4_5_20251001("claude-haiku-4-5-20251001"),
    CLAUDE_SONNET_4_5_20250929("claude-sonnet-4-5-20250929"),

    CLAUDE_OPUS_4_1_20250805("claude-opus-4-1-20250805"),

    CLAUDE_OPUS_4_20250514("claude-opus-4-20250514"),
    CLAUDE_SONNET_4_20250514("claude-sonnet-4-20250514"),

    /**
     * @deprecated Will be retired by Anthropic on February 19, 2026
     */
    @Deprecated(since = "October 28, 2025", forRemoval = true)
    CLAUDE_3_7_SONNET_20250219("claude-3-7-sonnet-20250219"),

    CLAUDE_3_5_HAIKU_20241022("claude-3-5-haiku-20241022"),

    /**
     * @deprecated Will be retired by Anthropic on January 5, 2026
     */
    @Deprecated(since = "June 30, 2025", forRemoval = true)
    CLAUDE_3_OPUS_20240229("claude-3-opus-20240229"),
    CLAUDE_3_HAIKU_20240307("claude-3-haiku-20240307");

    private final String stringValue;

    AnthropicChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
