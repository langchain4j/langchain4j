package dev.langchain4j.model.bedrock;

/**
 * Enum representing where to place cache points in the conversation.
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
 */
public enum BedrockCachePointPlacement {
    AFTER_SYSTEM,
    AFTER_USER_MESSAGE,
    AFTER_TOOLS
}
