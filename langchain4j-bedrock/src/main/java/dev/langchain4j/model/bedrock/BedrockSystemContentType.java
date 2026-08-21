package dev.langchain4j.model.bedrock;

/**
 * Types of content that can be included in a {@link BedrockSystemMessage}.
 * <p>
 * Currently only {@link #TEXT} is supported. Additional content types
 * (such as images) may be added in future versions.
 *
 * @see BedrockSystemContent
 * @see BedrockSystemTextContent
 * @since 1.11.0
 */
public enum BedrockSystemContentType {
    /**
     * Text content block.
     *
     * @see BedrockSystemTextContent
     */
    TEXT
}
