package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.ANTHROPIC;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createAnthropicRequest;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.validate;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.EPHEMERAL;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.NO_CACHE;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAiMessage;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toTokenUsage;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.anthropic.internal.client.ParsedAndRawResponse;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents an Anthropic language model with a Messages (chat) API.
 * <br>
 * More details are available <a href="https://docs.anthropic.com/claude/reference/messages_post">here</a>.
 * <br>
 * <br>
 * It supports tools. See more information <a href="https://docs.anthropic.com/claude/docs/tool-use">here</a>.
 * <br>
 * <br>
 * It supports {@link Image}s as inputs. {@link UserMessage}s can contain one or multiple {@link ImageContent}s.
 * {@link Image}s must not be represented as URLs; they should be Base64-encoded strings and include a {@code mimeType}.
 * <br>
 * <br>
 * The content of {@link SystemMessage}s is sent using the "system" parameter.
 * <br>
 * <br>
 * Supports caching {@link SystemMessage}s and {@link ToolSpecification}s.
 */
public class AnthropicChatModel implements ChatModel {

    private final AnthropicClient client;
    private final boolean cacheSystemMessages;
    private final boolean cacheTools;
    private final String thinkingType;
    private final Integer thinkingBudgetTokens;
    private final boolean returnThinking;
    private final boolean sendThinking;
    private final int maxRetries;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;
    private final String toolChoiceName;
    private final Boolean disableParallelToolUse;
    private final List<AnthropicServerTool> serverTools;
    private final boolean returnServerToolResults;
    private final Set<String> toolMetadataKeysToSend;
    private final String userId;
    private final Map<String, Object> customParameters;
    private final Boolean strictTools;
    private final Set<Capability> supportedCapabilities;

    public AnthropicChatModel(AnthropicChatModelBuilder builder) {
        this.client = AnthropicClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(builder.apiKey)
                .version(getOrDefault(builder.version, "2023-06-01"))
                .beta(builder.beta)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();

        this.cacheSystemMessages = getOrDefault(builder.cacheSystemMessages, false);
        this.cacheTools = getOrDefault(builder.cacheTools, false);
        this.thinkingType = builder.thinkingType;
        this.thinkingBudgetTokens = builder.thinkingBudgetTokens;
        this.returnThinking = getOrDefault(builder.returnThinking, false);
        this.sendThinking = getOrDefault(builder.sendThinking, true);
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.listeners = copy(builder.listeners);
        this.toolChoiceName = builder.toolChoiceName;
        this.disableParallelToolUse = builder.disableParallelToolUse;
        this.serverTools = copy(builder.serverTools);
        this.returnServerToolResults = getOrDefault(builder.returnServerToolResults, false);
        this.toolMetadataKeysToSend = copy(builder.toolMetadataKeysToSend);
        this.userId = builder.userId;
        this.customParameters = copy(builder.customParameters);
        this.strictTools = builder.strictTools;
        this.supportedCapabilities = copy(builder.supportedCapabilities);

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        this.defaultRequestParameters = DefaultChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .maxOutputTokens(
                        getOrDefault(builder.maxTokens, getOrDefault(commonParameters.maxOutputTokens(), 1024)))
                .stopSequences(getOrDefault(builder.stopSequences, commonParameters.stopSequences()))
                .toolSpecifications(getOrDefault(builder.toolSpecifications, commonParameters.toolSpecifications()))
                .toolChoice(getOrDefault(builder.toolChoice, commonParameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .build();
    }

    public static AnthropicChatModelBuilder builder() {
        return new AnthropicChatModelBuilder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        validate(chatRequest.parameters());

        AnthropicCreateMessageRequest anthropicRequest = createAnthropicRequest(
                chatRequest,
                toThinking(thinkingType, thinkingBudgetTokens),
                sendThinking,
                cacheSystemMessages ? EPHEMERAL : NO_CACHE,
                cacheTools ? EPHEMERAL : NO_CACHE,
                false,
                toolChoiceName,
                disableParallelToolUse,
                serverTools,
                toolMetadataKeysToSend,
                userId,
                customParameters,
                strictTools);

        ParsedAndRawResponse response =
                withRetryMappingExceptions(() -> client.createMessageWithRawResponse(anthropicRequest), maxRetries);

        return createChatResponse(response);
    }

    private ChatResponse createChatResponse(ParsedAndRawResponse parsedAndRawResponse) {
        AnthropicCreateMessageResponse response = parsedAndRawResponse.parsedResponse();
        AnthropicChatResponseMetadata responseMetadata = AnthropicChatResponseMetadata.builder()
                .id(response.id())
                .modelName(response.model())
                .tokenUsage(toTokenUsage(response.usage()))
                .finishReason(toFinishReason(response.stopReason()))
                .rawHttpResponse(parsedAndRawResponse.rawResponse())
                .build();

        return ChatResponse.builder()
                .aiMessage(toAiMessage(response.content(), returnThinking, returnServerToolResults))
                .metadata(responseMetadata)
                .build();
    }

    static AnthropicThinking toThinking(String thinkingType, Integer thinkingBudgetTokens) {
        if (thinkingType != null || thinkingBudgetTokens != null) {
            return AnthropicThinking.builder()
                    .type(thinkingType)
                    .budgetTokens(thinkingBudgetTokens)
                    .build();
        }
        return null;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ANTHROPIC;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    // In AnthropicChatModel.java
    public static class AnthropicChatModelBuilder extends AbstractAnthropicChatModelBuilder<AnthropicChatModelBuilder> {

        private Integer maxRetries;
        private List<ChatModelListener> listeners;
        private Set<Capability> supportedCapabilities;

        public AnthropicChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public AnthropicChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public AnthropicChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            this.supportedCapabilities = Arrays.stream(supportedCapabilities).collect(Collectors.toSet());
            return this;
        }

        public AnthropicChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public AnthropicChatModel build() {
            return new AnthropicChatModel(this);
        }
    }
}
