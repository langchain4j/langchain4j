package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.Objects;

/**
 * Text content block for {@link BedrockSystemMessage} with optional cache point.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Without cache point
 * BedrockSystemTextContent.from("Instructions");
 *
 * // With cache point (content will be cached)
 * BedrockSystemTextContent.withCachePoint("Large static context");
 * }</pre>
 * <p>
 * <b>AWS Bedrock Caching Requirements:</b>
 * <ul>
 *   <li>Minimum ~1,024 tokens required for caching to activate</li>
 *   <li>Cache has 5-minute TTL (resets on each hit)</li>
 *   <li>Only Claude 3.x and Amazon Nova models support caching</li>
 *   <li>Maximum 4 cache points per request (see {@link BedrockSystemMessage#MAX_CACHE_POINTS})</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> Instances of this class are immutable and thread-safe.
 *
 * @see BedrockSystemMessage
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
 * @since 1.11.0
 */
public final class BedrockSystemTextContent implements BedrockSystemContent {

    /**
     * Maximum allowed text length (1MB) to prevent resource exhaustion.
     */
    public static final int MAX_TEXT_LENGTH = 1_000_000;

    private static final int MAX_TOSTRING_LENGTH = 200;

    private final String text;
    private final boolean cachePoint;

    /**
     * Creates text content with optional cache point.
     *
     * @param text       the text content (must not be blank, max 1MB)
     * @param cachePoint whether to add a cache point after this content
     * @throws IllegalArgumentException if text is null, blank, or exceeds max length
     */
    public BedrockSystemTextContent(String text, boolean cachePoint) {
        this.text = ensureNotBlank(text, "text");
        ensureBetween(text.length(), 1, MAX_TEXT_LENGTH, "text length");
        this.cachePoint = cachePoint;
    }

    /**
     * Creates text content without cache point.
     *
     * @param text the text content (must not be blank)
     */
    public BedrockSystemTextContent(String text) {
        this(text, false);
    }

    /**
     * Returns the text content.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    @Override
    public boolean hasCachePoint() {
        return cachePoint;
    }

    @Override
    public BedrockSystemContentType type() {
        return BedrockSystemContentType.TEXT;
    }

    /**
     * Creates text content without cache point.
     *
     * @param text the text content
     * @return new BedrockSystemTextContent
     */
    public static BedrockSystemTextContent from(String text) {
        return new BedrockSystemTextContent(text, false);
    }

    /**
     * Creates text content WITH cache point marker.
     * A cache point will be inserted after this content in the Bedrock request.
     * <p>
     * <b>Note:</b> For caching to activate, content should be at least ~1,024 tokens.
     *
     * @param text the text content
     * @return new BedrockSystemTextContent with cache point
     */
    public static BedrockSystemTextContent withCachePoint(String text) {
        return new BedrockSystemTextContent(text, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BedrockSystemTextContent that = (BedrockSystemTextContent) o;
        return cachePoint == that.cachePoint && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, cachePoint);
    }

    @Override
    public String toString() {
        String truncatedText = text.length() > MAX_TOSTRING_LENGTH
                ? text.substring(0, MAX_TOSTRING_LENGTH) + "...[" + text.length() + " chars]"
                : text;
        return "BedrockSystemTextContent {" + " text = "
                + quoted(truncatedText) + ", cachePoint = "
                + cachePoint + " }";
    }
}
