package dev.langchain4j.model.bedrock;

/**
 * Represents a content block within a {@link BedrockSystemMessage}.
 * <p>
 * This is a sealed interface - only library-provided implementations are supported.
 * Currently, only {@link BedrockSystemTextContent} is available.
 * <p>
 * <b>Implementation Requirements:</b>
 * <ul>
 *   <li>Implementations must be immutable and thread-safe</li>
 *   <li>Implementations must provide proper {@code equals()} and {@code hashCode()}</li>
 *   <li>Cache points are inserted AFTER the content block in AWS Bedrock requests</li>
 *   <li>Note: Caching may not activate if content is below ~1,024 tokens</li>
 * </ul>
 *
 * @see BedrockSystemTextContent
 * @see BedrockSystemMessage
 * @since 1.11.0
 */
public sealed interface BedrockSystemContent permits BedrockSystemTextContent {

    /**
     * Returns the content type.
     *
     * @return the content type enum value
     */
    BedrockSystemContentType type();

    /**
     * Returns whether this content block has a cache point marker.
     * When true, a cache point will be inserted AFTER this content block
     * in the AWS Bedrock request.
     *
     * @return true if this content has a cache point
     */
    boolean hasCachePoint();
}
