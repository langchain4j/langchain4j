/**
 * AWS Bedrock integration for LangChain4j.
 *
 * <h2>Overview</h2>
 * This package provides integration with AWS Bedrock, Amazon's fully managed service
 * for foundation models. It includes chat models, streaming support, and prompt caching.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link dev.langchain4j.model.bedrock.BedrockChatModel} - Synchronous chat model</li>
 *   <li>{@link dev.langchain4j.model.bedrock.BedrockStreamingChatModel} - Streaming chat model</li>
 *   <li>{@link dev.langchain4j.model.bedrock.BedrockChatRequestParameters} - Bedrock-specific parameters</li>
 * </ul>
 *
 * <h2>Prompt Caching</h2>
 * AWS Bedrock supports prompt caching to reduce latency and costs for repeated content.
 * This package provides two approaches:
 *
 * <h3>1. Simple Caching with {@link dev.langchain4j.model.bedrock.BedrockCachePointPlacement}</h3>
 * <p>Use for automatic cache point placement after system messages, user messages, or tools:</p>
 * <pre>{@code
 * BedrockChatModel model = BedrockChatModel.builder()
 *     .modelId("anthropic.claude-3-sonnet...")
 *     .defaultRequestParameters(BedrockChatRequestParameters.builder()
 *         .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
 *         .build())
 *     .build();
 * }</pre>
 *
 * <h3>2. Granular Caching with {@link dev.langchain4j.model.bedrock.BedrockSystemMessage}</h3>
 * <p>Use for fine-grained control over which content is cached:</p>
 * <pre>{@code
 * BedrockSystemMessage systemMessage = BedrockSystemMessage.builder()
 *     .addText("You are an AI assistant.")        // Not cached
 *     .addTextWithCachePoint("Large examples...")  // Cached
 *     .addText("User: " + userName)                // Not cached
 *     .build();
 * }</pre>
 *
 * <h3>Choosing Between Approaches</h3>
 * <ul>
 *   <li><b>Use simple caching</b> when all system content is static and should be cached together</li>
 *   <li><b>Use granular caching</b> when you have mixed static/dynamic content within system messages</li>
 * </ul>
 *
 * <h3>AWS Bedrock Caching Requirements</h3>
 * <ul>
 *   <li><b>Minimum tokens:</b> ~1,024 tokens required for caching to activate</li>
 *   <li><b>Cache TTL:</b> 5-minute default, resets on each cache hit</li>
 *   <li><b>Maximum cache points:</b> 4 per request (across all messages)</li>
 *   <li><b>Supported models:</b> Claude 3.x and Amazon Nova models only</li>
 * </ul>
 *
 * <h2>BedrockSystemMessage vs SystemMessage</h2>
 * <p><b>Important:</b> {@link dev.langchain4j.model.bedrock.BedrockSystemMessage} implements
 * {@link dev.langchain4j.data.message.ChatMessage} but does NOT extend
 * {@link dev.langchain4j.data.message.SystemMessage}. This has implications:</p>
 * <ul>
 *   <li>{@code instanceof SystemMessage} will return {@code false}</li>
 *   <li>{@code SystemMessage.findFirst()} will not find BedrockSystemMessage</li>
 *   <li>ChatMemory window managers may not recognize it as a system message</li>
 *   <li>Serialization with ChatMessageSerializer is not supported</li>
 * </ul>
 * <p>Use {@link dev.langchain4j.model.bedrock.BedrockSystemMessage#toSystemMessage()} to convert
 * to a core SystemMessage when needed (cache points will be lost).</p>
 *
 * @see dev.langchain4j.model.bedrock.BedrockChatModel
 * @see dev.langchain4j.model.bedrock.BedrockStreamingChatModel
 * @see dev.langchain4j.model.bedrock.BedrockSystemMessage
 * @see dev.langchain4j.model.bedrock.BedrockCachePointPlacement
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock Prompt Caching</a>
 */
package dev.langchain4j.model.bedrock;
