package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AWS Bedrock-specific system message supporting granular cache points.
 * <p>
 * Unlike the core {@link SystemMessage} which contains a single text block,
 * this class supports multiple content blocks, each with an optional cache point.
 * This enables caching static portions (instructions, examples) while keeping
 * dynamic portions (user context) uncached.
 * <p>
 * <b>Example usage:</b>
 * <pre>{@code
 * BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
 *     .addText("You are an AI assistant.")
 *     .addTextWithCachePoint("Here are 50 examples:\n" + examples)  // Cached
 *     .addText("Current user: " + userName)  // Not cached
 *     .build();
 * }</pre>
 * <p>
 * <b>AWS Bedrock Caching Requirements:</b>
 * <ul>
 *   <li><b>Minimum tokens:</b> ~1,024 tokens required for caching to activate</li>
 *   <li><b>Cache TTL:</b> 5-minute default, resets on each cache hit</li>
 *   <li><b>Supported models:</b> Only Claude 3.x and Amazon Nova models</li>
 *   <li><b>Maximum cache points:</b> AWS limits to 4 cache points per request
 *       (across all messages including system, user, and tool definitions)</li>
 * </ul>
 * <p>
 * <b>Important limitations:</b>
 * <ul>
 *   <li><b>Serialization:</b> This message type is NOT compatible with standard
 *       {@code ChatMessageSerializer}. If using {@code ChatMemory} with persistence,
 *       convert to {@link SystemMessage} first using {@link #toSystemMessage()}
 *       (cache points will be lost).</li>
 *   <li><b>ChatMemory window:</b> {@code MessageWindowChatMemory} and
 *       {@code TokenWindowChatMemory} will NOT recognize this as a system message
 *       for window management (they use {@code instanceof SystemMessage}).</li>
 *   <li><b>Static helpers:</b> {@code SystemMessage.findFirst()}, {@code findAll()},
 *       {@code findLast()} will NOT find {@code BedrockSystemMessage} instances.</li>
 *   <li><b>Type checking:</b> {@link #type()} returns {@link ChatMessageType#SYSTEM},
 *       but this class does NOT extend {@link SystemMessage}. Always use
 *       {@code instanceof} checks rather than {@code type()} checks.
 *       <b>WARNING:</b> Code using {@code message.type() == ChatMessageType.SYSTEM}
 *       will match this class, but code using {@code instanceof SystemMessage} will NOT.
 *       The codebase has many places using {@code instanceof SystemMessage} that will
 *       silently ignore {@code BedrockSystemMessage} instances.</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> Instances of this class are immutable and thread-safe.
 * The {@link Builder} is NOT thread-safe and should not be shared between threads.
 *
 * @see BedrockSystemContent
 * @see BedrockCachePointPlacement
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
 * @since 1.11.0
 */
public class BedrockSystemMessage implements ChatMessage {

    /**
     * Maximum number of content blocks per message.
     */
    public static final int MAX_CONTENT_BLOCKS = 10;

    /**
     * Maximum number of cache points allowed by AWS Bedrock per request.
     */
    public static final int MAX_CACHE_POINTS = 4;

    private final List<BedrockSystemContent> contents;

    private BedrockSystemMessage(Builder builder) {
        ensureNotEmpty(builder.contents, "contents");
        ensureBetween(builder.contents.size(), 1, MAX_CONTENT_BLOCKS, "content block count");

        // Validate cache point count (AWS Bedrock limits to 4 per request)
        long cachePointCount = builder.contents.stream()
                .filter(BedrockSystemContent::hasCachePoint)
                .count();
        if (cachePointCount > MAX_CACHE_POINTS) {
            throw new IllegalArgumentException("Maximum " + MAX_CACHE_POINTS
                    + " cache points allowed per AWS Bedrock request, but got " + cachePointCount);
        }

        // Single defensive copy - builder list is already validated
        this.contents = Collections.unmodifiableList(new ArrayList<>(builder.contents));
    }

    /**
     * Returns an unmodifiable list of content blocks.
     *
     * @return the content blocks
     */
    public List<BedrockSystemContent> contents() {
        return contents;
    }

    /**
     * Returns {@link ChatMessageType#SYSTEM}.
     * <p>
     * <b>Note:</b> While this returns {@code SYSTEM}, this class does NOT extend
     * {@link SystemMessage}. Always use {@code instanceof} checks for type safety.
     *
     * @return ChatMessageType.SYSTEM
     */
    @Override
    public ChatMessageType type() {
        return ChatMessageType.SYSTEM;
    }

    /**
     * Returns combined text from all text content blocks, joined by double newlines.
     * Useful for logging or conversion to core SystemMessage.
     *
     * @return combined text from all blocks
     */
    public String text() {
        return contents.stream()
                .filter(c -> c instanceof BedrockSystemTextContent)
                .map(c -> ((BedrockSystemTextContent) c).text())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Returns true if this message contains exactly one text content block.
     *
     * @return true if single text block
     */
    public boolean hasSingleText() {
        return contents.size() == 1 && contents.get(0) instanceof BedrockSystemTextContent;
    }

    /**
     * Returns text from single content block.
     *
     * @return the single text content
     * @throws IllegalStateException if message has multiple content blocks
     */
    public String singleText() {
        if (!hasSingleText()) {
            throw new IllegalStateException(
                    "Expected single text content, but got " + contents.size() + " content blocks");
        }
        return ((BedrockSystemTextContent) contents.get(0)).text();
    }

    /**
     * Converts to core SystemMessage (loses cache point information).
     * Use this for ChatMemory persistence where serialization is required.
     *
     * @return equivalent SystemMessage (without cache points)
     */
    public SystemMessage toSystemMessage() {
        return SystemMessage.from(text());
    }

    /**
     * Creates a new builder initialized with this message's contents.
     *
     * @return new builder with current contents
     */
    public Builder toBuilder() {
        return new Builder().contents(new ArrayList<>(contents));
    }

    /**
     * Creates a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BedrockSystemMessage}.
     */
    public static class Builder {
        private List<BedrockSystemContent> contents = new ArrayList<>();

        /**
         * Sets all content blocks (replaces any existing).
         *
         * @param contents the content blocks (1-10 blocks, no nulls)
         * @return this builder
         * @throws IllegalArgumentException if contents is null, empty, has nulls, or exceeds max
         */
        public Builder contents(List<BedrockSystemContent> contents) {
            ensureNotEmpty(contents, "contents");
            ensureBetween(contents.size(), 1, MAX_CONTENT_BLOCKS, "content block count");
            for (int i = 0; i < contents.size(); i++) {
                ensureNotNull(contents.get(i), "contents[" + i + "]");
            }
            this.contents = new ArrayList<>(contents);
            return this;
        }

        /**
         * Adds a content block.
         *
         * @param content the content block
         * @return this builder
         * @throws IllegalArgumentException if content is null or max blocks exceeded
         */
        public Builder addContent(BedrockSystemContent content) {
            ensureNotNull(content, "content");
            if (this.contents.size() >= MAX_CONTENT_BLOCKS) {
                throw new IllegalArgumentException("Maximum " + MAX_CONTENT_BLOCKS + " content blocks allowed");
            }
            this.contents.add(content);
            return this;
        }

        /**
         * Adds text content WITHOUT cache point.
         *
         * @param text the text content
         * @return this builder
         * @throws IllegalArgumentException if text is null or blank
         */
        public Builder addText(String text) {
            return addContent(BedrockSystemTextContent.from(text));
        }

        /**
         * Adds text content WITH cache point marker.
         * A cache point will be inserted after this content in the Bedrock request.
         * <p>
         * <b>Note:</b> For caching to activate, content should be at least ~1,024 tokens.
         *
         * @param text the text content
         * @return this builder
         * @throws IllegalArgumentException if text is null or blank
         */
        public Builder addTextWithCachePoint(String text) {
            return addContent(BedrockSystemTextContent.withCachePoint(text));
        }

        /**
         * Builds the message.
         *
         * @return new BedrockSystemMessage
         */
        public BedrockSystemMessage build() {
            return new BedrockSystemMessage(this);
        }
    }

    // Static factory methods

    /**
     * Creates a simple message with single text content (no cache point).
     *
     * @param text the text content
     * @return new BedrockSystemMessage
     */
    public static BedrockSystemMessage from(String text) {
        return builder().addText(text).build();
    }

    /**
     * Creates a message from list of content blocks.
     *
     * @param contents the content blocks
     * @return new BedrockSystemMessage
     */
    public static BedrockSystemMessage from(List<BedrockSystemContent> contents) {
        return builder().contents(contents).build();
    }

    /**
     * Converts a core SystemMessage to BedrockSystemMessage (no cache point).
     *
     * @param systemMessage the core system message
     * @return new BedrockSystemMessage
     */
    public static BedrockSystemMessage from(SystemMessage systemMessage) {
        return from(systemMessage.text());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BedrockSystemMessage that = (BedrockSystemMessage) o;
        return Objects.equals(contents, that.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contents);
    }

    /**
     * Returns true if any content block has a cache point marker.
     *
     * @return true if this message contains any cache points
     */
    public boolean hasCachePoints() {
        return contents.stream().anyMatch(BedrockSystemContent::hasCachePoint);
    }

    /**
     * Returns the number of cache points in this message.
     *
     * @return the cache point count
     */
    public int cachePointCount() {
        return (int)
                contents.stream().filter(BedrockSystemContent::hasCachePoint).count();
    }

    @Override
    public String toString() {
        int cachePoints = cachePointCount();
        return "BedrockSystemMessage { contents = " + contents.size() + " blocks, cachePoints = " + cachePoints + " }";
    }
}
