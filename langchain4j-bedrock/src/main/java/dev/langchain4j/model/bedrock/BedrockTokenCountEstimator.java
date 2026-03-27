package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Objects.isNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseTokensRequest;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensInput;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensRequest;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;

/**
 * Estimates token counts using the AWS Bedrock CountTokens API with the Converse input format.
 * <p>
 * Messages are converted to the Bedrock Converse format in a single pass. Orphaned tool
 * interactions are sanitized inline to comply with the API's conversation structure rules:
 * <ul>
 *   <li>Orphaned {@link ToolExecutionResultMessage}s (no preceding {@link AiMessage} with
 *       {@code tool_use}) are silently skipped.</li>
 *   <li>Orphaned {@code tool_use} blocks in an {@link AiMessage} (no following
 *       {@link ToolExecutionResultMessage}) receive a dummy {@code tool_result} with an
 *       error status, both at the end of the conversation (trailing case) and mid-conversation
 *       (e.g. when a {@link UserMessage} directly follows an {@link AiMessage} with
 *       {@code tool_use} and no {@link ToolExecutionResultMessage} in between). This includes
 *       partial cases where an {@link AiMessage} contains multiple {@code tool_use} blocks but
 *       only some have matching results — unmatched blocks receive dummy results while matched
 *       ones are preserved. This preserves the conversation structure the model expects,
 *       avoiding message removal or adjacent same-role messages that would be rejected by the
 *       API.</li>
 * </ul>
 * This can occur when {@code TokenWindowChatMemory} evicts
 * messages, when an {@link AiMessage} with {@code tool_use} is added to memory before
 * the tool is executed, or when the user interrupts a tool execution and continues
 * the conversation.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_CountTokens.html">CountTokens API</a>
 * @since 1.13.0
 */
@Experimental
public class BedrockTokenCountEstimator implements TokenCountEstimator {

    private static final Logger log = LoggerFactory.getLogger(BedrockTokenCountEstimator.class);

    private final BedrockRuntimeClient client;
    private final String modelId;

    public BedrockTokenCountEstimator(Builder builder) {
        this.modelId = ensureNotBlank(builder.modelId, "modelId");
        this.client = isNull(builder.client) ? createClient(builder) : builder.client;
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return estimateTokenCountInMessages(List.of(UserMessage.from(text)));
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInMessages(List.of(message));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        List<SystemContentBlock> systemBlocks = new ArrayList<>();
        List<Message> conversationMessages = new ArrayList<>();
        List<ContentBlock> pendingToolResults = new ArrayList<>();
        int pendingToolUseIndex = -1;

        for (ChatMessage message : messages) {
            if (message instanceof BedrockSystemMessage bedrockSystemMsg) {
                for (BedrockSystemContent content : bedrockSystemMsg.contents()) {
                    if (content instanceof BedrockSystemTextContent textContent) {
                        systemBlocks.add(SystemContentBlock.builder()
                                .text(textContent.text())
                                .build());
                    }
                }
            } else if (message instanceof SystemMessage systemMsg) {
                systemBlocks.add(
                        SystemContentBlock.builder().text(systemMsg.text()).build());
            } else if (message instanceof UserMessage userMsg) {
                sanitizeAndFlush(conversationMessages, pendingToolResults, pendingToolUseIndex);
                pendingToolUseIndex = -1;
                conversationMessages.add(Message.builder()
                        .role(ConversationRole.USER)
                        .content(BedrockMessageConverter.convertContents(userMsg.contents()))
                        .build());
            } else if (message instanceof AiMessage aiMsg) {
                sanitizeAndFlush(conversationMessages, pendingToolResults, pendingToolUseIndex);
                pendingToolUseIndex = -1;
                List<ContentBlock> blocks = new ArrayList<>();
                if (aiMsg.thinking() != null) {
                    blocks.add(ContentBlock.builder()
                            .reasoningContent(ReasoningContentBlock.builder()
                                    .reasoningText(ReasoningTextBlock.builder()
                                            .text(aiMsg.thinking())
                                            .signature(aiMsg.attribute("thinking_signature", String.class))
                                            .build())
                                    .build())
                            .build());
                }
                if (aiMsg.text() != null) {
                    blocks.add(ContentBlock.builder().text(aiMsg.text()).build());
                }
                if (aiMsg.hasToolExecutionRequests()) {
                    blocks.addAll(BedrockMessageConverter.convertToolRequests(aiMsg.toolExecutionRequests()));
                }
                conversationMessages.add(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(blocks)
                        .build());
                if (aiMsg.hasToolExecutionRequests()) {
                    pendingToolUseIndex = conversationMessages.size() - 1;
                }
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                if (pendingToolUseIndex < 0) {
                    continue; // Orphaned tool result — preceding tool_use was evicted
                }
                pendingToolResults.add(BedrockMessageConverter.createToolResultBlock(toolResult));
            }
        }

        // Final sanitization pass
        sanitizeAndFlush(conversationMessages, pendingToolResults, pendingToolUseIndex);

        ConverseTokensRequest.Builder converseBuilder = ConverseTokensRequest.builder();
        if (!systemBlocks.isEmpty()) {
            converseBuilder.system(systemBlocks);
        }
        if (!conversationMessages.isEmpty()) {
            converseBuilder.messages(conversationMessages);
        }

        CountTokensRequest request = CountTokensRequest.builder()
                .modelId(modelId)
                .input(CountTokensInput.fromConverse(converseBuilder.build()))
                .build();

        CountTokensResponse response = client.countTokens(request);
        return response.inputTokens();
    }

    /**
     * Injects dummy tool_results for orphaned tool_use blocks (those without a matching
     * tool_result), then flushes all results as a USER message.
     * <p>
     * This preserves conversation structure — the AiMessage is never modified or removed,
     * avoiding adjacent same-role messages that the Bedrock API would reject.
     * Orphaned tool_results (no preceding tool_use) are handled earlier via the
     * {@code pendingToolUseIndex < 0} check in the main loop.
     */
    private static void sanitizeAndFlush(
            List<Message> messages, List<ContentBlock> pendingToolResults, int toolUseIndex) {
        if (toolUseIndex >= 0) {
            Set<String> resultIds = pendingToolResults.isEmpty()
                    ? Set.of()
                    : pendingToolResults.stream()
                            .filter(b -> b.toolResult() != null)
                            .map(b -> b.toolResult().toolUseId())
                            .collect(Collectors.toSet());

            // Inject dummy tool_result for each tool_use that has no matching result
            Message msg = messages.get(toolUseIndex);
            for (ContentBlock block : msg.content()) {
                if (block.toolUse() != null
                        && !resultIds.contains(block.toolUse().toolUseId())) {
                    pendingToolResults.add(createDummyToolResult(block.toolUse().toolUseId()));
                }
            }
        }

        if (!pendingToolResults.isEmpty()) {
            messages.add(Message.builder()
                    .role(ConversationRole.USER)
                    .content(new ArrayList<>(pendingToolResults))
                    .build());
            pendingToolResults.clear();
        }
    }

    private static ContentBlock createDummyToolResult(String toolUseId) {
        return ContentBlock.builder()
                .toolResult(ToolResultBlock.builder()
                        .toolUseId(toolUseId)
                        .status(ToolResultStatus.ERROR)
                        .content(ToolResultContentBlock.builder()
                                .text("Tool execution was interrupted")
                                .build())
                        .build())
                .build();
    }

    private BedrockRuntimeClient createClient(Builder builder) {
        Region region = getOrDefault(builder.region, Region.US_EAST_1);
        Duration timeout = getOrDefault(builder.timeout, Duration.ofMinutes(1));
        boolean logReq = getOrDefault(builder.logRequests, false);
        boolean logResp = getOrDefault(builder.logResponses, false);

        return BedrockRuntimeClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(config -> {
                    config.apiCallTimeout(timeout);
                    if (logReq || logResp) {
                        config.addExecutionInterceptor(new AwsLoggingInterceptor(logReq, logResp, log));
                    }
                })
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private BedrockRuntimeClient client;
        private String modelId;
        private Region region;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;

        public Builder client(BedrockRuntimeClient client) {
            this.client = client;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public BedrockTokenCountEstimator build() {
            return new BedrockTokenCountEstimator(this);
        }
    }
}
