package dev.langchain4j.model.anthropic;

/**
 * See more details <a href="https://docs.anthropic.com/claude/docs/models-overview">here</a>.
 */
public enum AnthropicChatModelName {

    CLAUDE_OPUS_4_6("claude-opus-4-6"),
    CLAUDE_SONNET_4_6("claude-sonnet-4-6"),

    CLAUDE_OPUS_4_5_20251101("claude-opus-4-5-20251101"),
    CLAUDE_SONNET_4_5_20250929("claude-sonnet-4-5-20250929"),
    CLAUDE_HAIKU_4_5_20251001("claude-haiku-4-5-20251001"),

    CLAUDE_OPUS_4_1_20250805("claude-opus-4-1-20250805"),

    CLAUDE_OPUS_4_20250514("claude-opus-4-20250514"),
    CLAUDE_SONNET_4_20250514("claude-sonnet-4-20250514"),

    @Deprecated
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
