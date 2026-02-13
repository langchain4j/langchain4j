package dev.langchain4j.model.bedrock;

/**
 * See more details <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html">here</a>.
 */
public enum BedrockChatModelName {
    ANTHROPIC_CLAUDE_4_5_HAIKU_V1_0("anthropic.claude-haiku-4-5-20251001-v1:0"),
    ANTHROPIC_CLAUDE_4_5_SONNET_V1_0("anthropic.claude-sonnet-4-5-20250929-v1:0"),
    ANTHROPIC_CLAUDE_4_5_OPUS_V1_0("anthropic.claude-opus-4-5-20251101-v1:0"),
    ANTHROPIC_CLAUDE_4_6_OPUS_V1("anthropic.claude-opus-4-6-v1"),

    MISTRAL_LARGE_2402_V1_0("mistral.mistral-large-2402-v1:0");

    private final String stringValue;

    BedrockChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
