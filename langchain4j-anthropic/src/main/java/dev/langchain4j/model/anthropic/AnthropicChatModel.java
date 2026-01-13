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
import static java.util.Arrays.asList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
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
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

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

    public static class AnthropicChatModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String version;
        private String beta;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer maxTokens;
        private List<String> stopSequences;
        private ResponseFormat responseFormat;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private String toolChoiceName;
        private Boolean disableParallelToolUse;
        private List<AnthropicServerTool> serverTools;
        private Boolean returnServerToolResults;
        private Set<String> toolMetadataKeysToSend;
        private Boolean cacheSystemMessages;
        private Boolean cacheTools;
        private String thinkingType;
        private Integer thinkingBudgetTokens;
        private Boolean returnThinking;
        private Boolean sendThinking;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;
        private String userId;
        private Map<String, Object> customParameters;
        private Boolean strictTools;
        private Set<Capability> supportedCapabilities;

        public AnthropicChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public AnthropicChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public AnthropicChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public AnthropicChatModelBuilder version(String version) {
            this.version = version;
            return this;
        }

        public AnthropicChatModelBuilder beta(String beta) {
            this.beta = beta;
            return this;
        }

        public AnthropicChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AnthropicChatModelBuilder modelName(AnthropicChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public AnthropicChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public AnthropicChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public AnthropicChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public AnthropicChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public AnthropicChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public AnthropicChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public AnthropicChatModelBuilder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public AnthropicChatModelBuilder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public AnthropicChatModelBuilder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public AnthropicChatModelBuilder toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return this;
        }

        public AnthropicChatModelBuilder disableParallelToolUse(Boolean disableParallelToolUse) {
            this.disableParallelToolUse = disableParallelToolUse;
            return this;
        }

        /**
         * Specifies server tools to be included in each request to the Anthropic API. For example:
         * <pre>
         * AnthropicServerTool webSearchTool = AnthropicServerTool.builder()
         *     .type("web_search_20250305")
         *     .name("web_search")
         *     .addAttribute("max_uses", 5)
         *     .addAttribute("allowed_domains", List.of("accuweather.com"))
         *     .build();
         * </pre>
         */
        public AnthropicChatModelBuilder serverTools(List<AnthropicServerTool> serverTools) {
            this.serverTools = serverTools;
            return this;
        }

        /**
         * Controls whether to return server tool results (e.g., web_search, code_execution)
         * inside {@link AiMessage#attributes()} under the key "server_tool_results".
         * <p>
         * Disabled by default to avoid polluting ChatMemory with potentially large data.
         * If enabled, server tool results will be stored as a {@code List<AnthropicServerToolResult>}
         * within the AiMessage attributes.
         *
         * @see #serverTools(List)
         */
        public AnthropicChatModelBuilder returnServerToolResults(Boolean returnServerToolResults) {
            this.returnServerToolResults = returnServerToolResults;
            return this;
        }

        /**
         * Specifies server tools to be included in each request to the Anthropic API. For example:
         * <pre>
         * AnthropicServerTool webSearchTool = AnthropicServerTool.builder()
         *     .type("web_search_20250305")
         *     .name("web_search")
         *     .addAttribute("max_uses", 5)
         *     .addAttribute("allowed_domains", List.of("accuweather.com"))
         *     .build();
         * </pre>
         */
        public AnthropicChatModelBuilder serverTools(AnthropicServerTool... serverTools) {
            return serverTools(asList(serverTools));
        }

        /**
         * Specifies metadata keys from the {@link ToolSpecification#metadata()} to be included in the request.
         */
        public AnthropicChatModelBuilder toolMetadataKeysToSend(Set<String> toolMetadataKeysToSend) {
            this.toolMetadataKeysToSend = toolMetadataKeysToSend;
            return this;
        }

        /**
         * Specifies metadata keys from the {@link ToolSpecification#metadata()} to be included in the request.
         */
        public AnthropicChatModelBuilder toolMetadataKeysToSend(String... toolMetadataKeysToSend) {
            return toolMetadataKeysToSend(new HashSet<>(asList(toolMetadataKeysToSend)));
        }

        public AnthropicChatModelBuilder cacheSystemMessages(Boolean cacheSystemMessages) {
            this.cacheSystemMessages = cacheSystemMessages;
            return this;
        }

        public AnthropicChatModelBuilder cacheTools(Boolean cacheTools) {
            this.cacheTools = cacheTools;
            return this;
        }

        /**
         * Enables <a href="https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking">thinking</a>.
         */
        public AnthropicChatModelBuilder thinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        /**
         * Configures <a href="https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking">thinking</a>.
         */
        public AnthropicChatModelBuilder thinkingBudgetTokens(Integer thinkingBudgetTokens) {
            this.thinkingBudgetTokens = thinkingBudgetTokens;
            return this;
        }

        /**
         * Controls whether to return thinking/reasoning text (if available) inside {@link AiMessage#thinking()}.
         * Please note that this does not enable thinking/reasoning for the LLM;
         * it only controls whether to parse the {@code thinking} field from the API response
         * and return it inside the {@link AiMessage}.
         * <p>
         * Disabled by default.
         * If enabled, the thinking text will be stored within the {@link AiMessage} and may be persisted.
         * If enabled, thinking signatures will also be stored and returned inside the {@link AiMessage#attributes()}.
         *
         * @see #thinkingType(String)
         * @see #thinkingBudgetTokens(Integer)
         * @see #sendThinking(Boolean)
         */
        public AnthropicChatModelBuilder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        /**
         * Controls whether to send thinking/reasoning text to the LLM in follow-up requests.
         * <p>
         * Enabled by default.
         * If enabled, the contents of {@link AiMessage#thinking()} will be sent in the API request.
         * If enabled, thinking signatures (inside the {@link AiMessage#attributes()}) will also be sent.
         *
         * @see #thinkingType(String)
         * @see #thinkingBudgetTokens(Integer)
         * @see #returnThinking(Boolean)
         */
        public AnthropicChatModelBuilder sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            return this;
        }

        public AnthropicChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public AnthropicChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public AnthropicChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public AnthropicChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public AnthropicChatModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public AnthropicChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public AnthropicChatModelBuilder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        /**
         * Sets the user ID for the requests.
         * This should be a uuid, hash value, or other opaque identifier.
         * Anthropic may use this id to help detect abuse.
         * Do not include any identifying information such as name, email address, or phone number.
         *
         * @param userId the user identifier
         * @return this builder
         */
        public AnthropicChatModelBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public AnthropicChatModelBuilder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        public AnthropicChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
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
                .id(response.id)
                .modelName(response.model)
                .tokenUsage(toTokenUsage(response.usage))
                .finishReason(toFinishReason(response.stopReason))
                .rawHttpResponse(parsedAndRawResponse.rawResponse())
                .build();

        return ChatResponse.builder()
                .aiMessage(toAiMessage(response.content, returnThinking, returnServerToolResults))
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
}
