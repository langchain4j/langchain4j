package dev.langchain4j.model.bedrock;

/**
 * Enum representing where to place cache points in the conversation.
 * <p>
 * <b>AWS Bedrock Caching Requirements:</b>
 * <ul>
 *   <li><b>Minimum tokens:</b> ~1,024 tokens required for caching to activate</li>
 *   <li><b>Cache TTL:</b> 5-minute default, resets on each cache hit</li>
 *   <li><b>Supported models:</b> Only Claude 3.x and Amazon Nova models</li>
 * </ul>
 * <p>
 * <b>Note on BedrockSystemMessage:</b> When using {@link BedrockSystemMessage} with
 * granular cache points, the {@link #AFTER_SYSTEM} placement is ignored for
 * {@code BedrockSystemMessage} instances. Granular cache points defined within
 * {@code BedrockSystemMessage} take precedence. The {@code AFTER_SYSTEM} placement
 * only applies when the LAST system-type message is a core
 * {@link dev.langchain4j.data.message.SystemMessage}.
 *
 * @see BedrockSystemMessage
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
 */
public enum BedrockCachePointPlacement {
    /**
     * Cache point after system messages.
     * <p>
     * Only applies when the LAST system-type message is a core {@code SystemMessage}.
     * Ignored when the last system message is a {@code BedrockSystemMessage}
     * (which uses its own granular cache points).
     */
    AFTER_SYSTEM,

    /**
     * Cache point after first user message.
     */
    AFTER_USER_MESSAGE,

    /**
     * Cache point after tool definitions.
     */
    AFTER_TOOLS
}
