package dev.langchain4j.model.bedrock;

/**
 * Defines which user messages should be wrapped in Bedrock Converse {@code guardContent} blocks.
 */
public enum BedrockGuardContentPlacement {

    /**
     * Wraps supported content blocks in the last user message.
     */
    LAST_USER_MESSAGE,

    /**
     * Wraps supported content blocks in all user messages.
     */
    ALL_USER_MESSAGES
}
