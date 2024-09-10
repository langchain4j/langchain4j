package dev.langchain4j.model.anthropic;

/**
 * See more details <a href="https://docs.anthropic.com/claude/docs/models-overview">here</a>.
 */
public enum AnthropicChatModelName {

    CLAUDE_3_OPUS_20240229("claude-3-opus-20240229"),
    CLAUDE_3_SONNET_20240229("claude-3-sonnet-20240229"),
    CLAUDE_3_HAIKU_20240307("claude-3-haiku-20240307"),
    CLAUDE_3_5_SONNET_20240620("claude-3-5-sonnet-20240620"),

    CLAUDE_2_1("claude-2.1"),
    CLAUDE_2("claude-2.0"),

    CLAUDE_INSTANT_1_2("claude-instant-1.2");

    private final String stringValue;

    AnthropicChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
