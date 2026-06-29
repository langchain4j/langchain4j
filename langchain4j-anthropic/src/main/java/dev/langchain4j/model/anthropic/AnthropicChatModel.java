package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.ANTHROPIC;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.addSkillsBeta;
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
import java.util.function.Supplier;
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

    public static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AnthropicClient client;
    private final String thinkingDisplay;
    private final int maxRetries;
    private final List<ChatModelListener> listeners;
    private final AnthropicChatRequestParameters defaultRequestParameters;
    private final List<AnthropicServerTool> serverTools;
    private final boolean returnServerToolResults;
    private final Set<String> toolMetadataKeysToSend;
    private final List<AnthropicSkill> skills;
    private final Map<String, Object> customParameters;
    private final Boolean strictTools;
    private final Set<Capability> supportedCapabilities;

    public AnthropicChatModel(AnthropicChatModelBuilder builder) {
        this.client = AnthropicClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(builder.apiKey)
                .version(getOrDefault(builder.version, ANTHROPIC_VERSION))
                .beta(addSkillsBeta(builder.beta, builder.skills))
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .customHeaders(builder.customHeadersSupplier)
                .build();

        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.listeners = copy(builder.listeners);
        this.returnServerToolResults = getOrDefault(builder.returnServerToolResults, false);
        this.supportedCapabilities = copy(builder.supportedCapabilities);

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        AnthropicChatRequestParameters anthropicDefaults = commonParameters instanceof AnthropicChatRequestParameters
                ? (AnthropicChatRequestParameters) commonParameters
                : AnthropicChatRequestParameters.EMPTY;

        this.thinkingDisplay = builder.thinkingDisplay;
        this.serverTools = copy(builder.serverTools);
        this.toolMetadataKeysToSend = copy(builder.toolMetadataKeysToSend);
        this.skills = copy(builder.skills);
        this.customParameters = copy(builder.customParameters);
        this.strictTools = builder.strictTools;

        this.defaultRequestParameters = AnthropicChatRequestParameters.builder()
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
                .cacheSystemMessages(getOrDefault(builder.cacheSystemMessages, anthropicDefaults.cacheSystemMessages()))
                .cacheTools(getOrDefault(builder.cacheTools, anthropicDefaults.cacheTools()))
                .thinkingType(getOrDefault(builder.thinkingType, anthropicDefaults.thinkingType()))
                .thinkingBudgetTokens(
                        getOrDefault(builder.thinkingBudgetTokens, anthropicDefaults.thinkingBudgetTokens()))
                .sendThinking(getOrDefault(builder.sendThinking, anthropicDefaults.sendThinking()))
                .midConversationSystemMessages(
                        getOrDefault(builder.midConversationSystemMessages, anthropicDefaults.midConversationSystemMessages()))
                .returnThinking(getOrDefault(builder.returnThinking, anthropicDefaults.returnThinking()))
                .toolChoiceName(getOrDefault(builder.toolChoiceName, anthropicDefaults.toolChoiceName()))
                .disableParallelToolUse(
                        getOrDefault(builder.disableParallelToolUse, anthropicDefaults.disableParallelToolUse()))
                .userId(getOrDefault(builder.userId, anthropicDefaults.userId()))
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
        private List<AnthropicSkill> skills;
        private Boolean cacheSystemMessages;
        private Boolean cacheTools;
        private String thinkingType;
        private Integer thinkingBudgetTokens;
        private String thinkingDisplay;
        private Boolean returnThinking;
        private Boolean sendThinking;
        private Boolean midConversationSystemMessages;
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
        private Supplier<Map<String, String>> customHeadersSupplier;

        /**
         * Sets a custom {@link HttpClientBuilder} for the underlying HTTP client.
         * Use this to configure timeouts, proxies, or other HTTP-level settings.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public AnthropicChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the base URL of the Anthropic API.
         * <p>
         * Defaults to {@code https://api.anthropic.com/v1/}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public AnthropicChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Anthropic API key used to authenticate requests.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public AnthropicChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the value of the {@code anthropic-version} request header.
         * <p>
         * Defaults to {@code 2023-06-01}.
         * See the <a href="https://docs.anthropic.com/en/api/versioning">Anthropic API versioning docs</a>.
         *
         * @param version the API version string
         * @return {@code this}
         */
        public AnthropicChatModelBuilder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the value of the {@code anthropic-beta} request header to opt into beta features.
         * <p>
         * See the <a href="https://docs.anthropic.com/en/api/beta-headers">Anthropic beta headers docs</a>.
         *
         * @param beta the beta feature identifier
         * @return {@code this}
         */
        public AnthropicChatModelBuilder beta(String beta) {
            this.beta = beta;
            return this;
        }

        /**
         * Sets the model to use for chat completions, specified as a string model ID.
         * <p>
         * See {@link AnthropicChatModelName} for available model constants.
         *
         * @param modelName the model ID, e.g. {@code "claude-opus-4-5"}
         * @return {@code this}
         */
        public AnthropicChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the model to use for chat completions using a type-safe enum constant.
         *
         * @param modelName the model name enum value
         * @return {@code this}
         */
        public AnthropicChatModelBuilder modelName(AnthropicChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * Sets the sampling temperature in the range {@code [0.0, 1.0]}.
         * Higher values produce more random output; lower values produce more deterministic output.
         * <p>
         * Cannot be used together with {@link #topP(Double)}.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public AnthropicChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling probability (top-p) in the range {@code (0.0, 1.0]}.
         * Only the tokens whose cumulative probability exceeds this threshold are considered.
         * <p>
         * Cannot be used together with {@link #temperature(Double)}.
         *
         * @param topP the nucleus sampling threshold
         * @return {@code this}
         */
        public AnthropicChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the top-K sampling value. Only the {@code topK} most-likely next tokens are considered at each step.
         * <p>
         * Recommended for advanced use only; {@link #temperature(Double)} is usually sufficient.
         *
         * @param topK the number of top tokens to sample from
         * @return {@code this}
         */
        public AnthropicChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Sets the maximum number of tokens to generate in the response.
         * <p>
         * Defaults to {@code 1024} if not set.
         *
         * @param maxTokens the maximum number of output tokens
         * @return {@code this}
         */
        public AnthropicChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets sequences that, when generated, will cause the model to stop generating further tokens.
         *
         * @param stopSequences the list of stop sequences
         * @return {@code this}
         */
        public AnthropicChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        /**
         * Sets the response format, enabling structured output such as JSON mode.
         *
         * @param responseFormat the desired response format
         * @return {@code this}
         */
        public AnthropicChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Sets the list of tools (functions) available to the model for function calling.
         *
         * @param toolSpecifications the tool specifications
         * @return {@code this}
         */
        public AnthropicChatModelBuilder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        /**
         * Sets the tools (functions) available to the model for function calling.
         *
         * @param toolSpecifications the tool specifications
         * @return {@code this}
         */
        public AnthropicChatModelBuilder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        /**
         * Controls how the model uses tools.
         * <p>
         * Use {@link ToolChoice#AUTO} to let the model decide, {@link ToolChoice#REQUIRED} to force tool use,
         * or {@link ToolChoice#NONE} to disable tool use.
         *
         * @param toolChoice the tool choice strategy
         * @return {@code this}
         */
        public AnthropicChatModelBuilder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        /**
         * Sets the name of the specific tool the model must use when {@link ToolChoice}
         * is set to {@link ToolChoice#REQUIRED}.
         *
         * @param toolChoiceName the name of the tool to force
         * @return {@code this}
         */
        public AnthropicChatModelBuilder toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return this;
        }

        /**
         * When set to {@code true}, prevents the model from calling multiple tools in a single response turn.
         *
         * @param disableParallelToolUse whether to disable parallel tool use
         * @return {@code this}
         */
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
         * Enables Anthropic <a href="https://docs.anthropic.com/en/docs/agents-and-tools/agent-skills/overview">Agent
         * Skills</a> so Claude can generate real downloadable documents (e.g. {@code .xlsx}, {@code .pptx},
         * {@code .docx}, {@code .pdf}). For example:
         * <pre>
         * AnthropicChatModel model = AnthropicChatModel.builder()
         *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
         *     .modelName("claude-opus-4-8")
         *     .maxTokens(4096)
         *     .skills(AnthropicSkill.XLSX, AnthropicSkill.PPTX)
         *     .returnServerToolResults(true)
         *     .build();
         * </pre>
         * Enabling skills automatically adds the {@code container.skills} block, the {@code code_execution} server tool
         * (unless already configured via {@link #serverTools(List)}) and the required {@code anthropic-beta} headers, so
         * none of that needs to be wired up manually.
         * <p>
         * Combine with {@link #returnServerToolResults(Boolean)} to surface the generated file ids under the
         * {@code "server_tool_results"} key of {@link AiMessage#attributes()}.
         * <p>
         * Skills are supported on Claude Sonnet 4 / 4.5, Opus 4 and later. At most 8 skills may be enabled per request.
         */
        public AnthropicChatModelBuilder skills(List<AnthropicSkill> skills) {
            this.skills = skills;
            return this;
        }

        /**
         * @see #skills(List)
         */
        public AnthropicChatModelBuilder skills(AnthropicSkill... skills) {
            return skills(asList(skills));
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

        /**
         * Enables prompt caching for {@link SystemMessage}s.
         * <p>
         * When {@code true}, system messages are sent with the {@code cache_control} header to allow
         * Anthropic to cache them across requests, reducing cost and latency for repeated prompts.
         * See the <a href="https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching">prompt caching docs</a>.
         *
         * @param cacheSystemMessages whether to cache system messages
         * @return {@code this}
         */
        public AnthropicChatModelBuilder cacheSystemMessages(Boolean cacheSystemMessages) {
            this.cacheSystemMessages = cacheSystemMessages;
            return this;
        }

        /**
         * Enables prompt caching for {@link ToolSpecification}s.
         * <p>
         * When {@code true}, tool definitions are sent with the {@code cache_control} header to allow
         * Anthropic to cache them across requests.
         * See the <a href="https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching">prompt caching docs</a>.
         *
         * @param cacheTools whether to cache tool definitions
         * @return {@code this}
         */
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
         * Controls how thinking content is returned in the response.
         * <p>
         * Valid values: {@code "summarized"} and {@code "omitted"}. On Claude Opus 4.7
         * the server default is {@code "omitted"}; on earlier Opus/Sonnet models the
         * default is {@code "summarized"}. Set to {@code "summarized"} explicitly on
         * Opus 4.7+ to restore visible thinking text for UIs that render it.
         *
         * @see #thinkingType(String)
         * @see #returnThinking(Boolean)
         */
        public AnthropicChatModelBuilder thinkingDisplay(String thinkingDisplay) {
            this.thinkingDisplay = thinkingDisplay;
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

        /**
         * Controls whether a {@link SystemMessage} that appears after the conversation has started (a
         * <em>mid-conversation</em> system message) is sent inline as a {@code system} entry in the
         * {@code messages} array, instead of being merged into the top-level {@code system} prompt.
         * <p>
         * Disabled by default, in which case every {@link SystemMessage} is sent via the top-level
         * {@code system} prompt regardless of position (the existing behavior). When enabled, leading
         * {@link SystemMessage}s still populate the top-level {@code system} prompt, while later ones are
         * sent inline so they take effect from that point in the conversation onward.
         * <p>
         * Supported only by Claude Opus 4.8. Anthropic also constrains placement: a mid-conversation system
         * message must immediately follow a {@code user} turn (including a {@code user} turn carrying tool
         * results) and must not sit between a {@code tool_use} block and its {@code tool_result}; an
         * unsupported model or an invalid placement returns a 400. This library does not reorder messages;
         * it sends them at the position the caller provided.
         *
         * @param midConversationSystemMessages whether to send mid-conversation system messages inline
         * @return {@code this}
         * @see <a href="https://platform.claude.com/docs/en/build-with-claude/mid-conversation-system-messages">Anthropic: mid-conversation system messages</a>
         */
        public AnthropicChatModelBuilder midConversationSystemMessages(Boolean midConversationSystemMessages) {
            this.midConversationSystemMessages = midConversationSystemMessages;
            return this;
        }

        /**
         * Sets the HTTP request timeout for calls to the Anthropic API.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public AnthropicChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the number of times to retry a request on transient errors (e.g. rate limits, server errors).
         * <p>
         * Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retry attempts
         * @return {@code this}
         */
        public AnthropicChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Enables debug logging of HTTP request bodies sent to the Anthropic API.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public AnthropicChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of HTTP response bodies received from the Anthropic API.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
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

        /**
         * Sets the list of {@link ChatModelListener}s to be notified on each request and response.
         * Useful for logging, metrics, and observability integrations.
         *
         * @param listeners the chat model listeners
         * @return {@code this}
         */
        public AnthropicChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Sets the {@link ChatModelListener}s to be notified on each request and response.
         *
         * @param listeners the chat model listeners
         * @return {@code this}
         */
        public AnthropicChatModelBuilder listeners(ChatModelListener... listeners) {
            return listeners(asList(listeners));
        }

        /**
         * Sets default {@link ChatRequestParameters} that are merged into every request.
         * Individual request parameters take precedence over these defaults.
         *
         * @param parameters the default request parameters
         * @return {@code this}
         */
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

        /**
         * Sets arbitrary extra parameters to include in the Anthropic API request body.
         * Use this for experimental or provider-specific fields not yet covered by dedicated builder methods.
         *
         * @param customParameters a map of parameter names to values
         * @return {@code this}
         */
        public AnthropicChatModelBuilder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        /**
         * Enables strict JSON schema validation for tool input parameters.
         * When {@code true}, the model enforces the exact schema defined in {@link ToolSpecification}.
         *
         * @param strictTools whether to enable strict tool schema validation
         * @return {@code this}
         */
        public AnthropicChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        /**
         * Sets extra HTTP headers to include in every request to the Anthropic API.
         *
         * @param customHeaders a map of header names to values
         * @return {@code this}
         */
        public AnthropicChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier of extra HTTP headers to include in every request to the Anthropic API.
         * The supplier is called once per request, allowing dynamic header values.
         *
         * @param customHeadersSupplier a supplier that returns a map of header names to values
         * @return {@code this}
         */
        public AnthropicChatModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Declares the capabilities supported by the model (e.g. {@link Capability#RESPONSE_FORMAT_JSON_SCHEMA}).
         * This influences how LangChain4j generates requests for this model.
         *
         * @param supportedCapabilities the capabilities to declare
         * @return {@code this}
         */
        public AnthropicChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            this.supportedCapabilities = Arrays.stream(supportedCapabilities).collect(Collectors.toSet());
            return this;
        }

        /**
         * Declares the capabilities supported by the model.
         *
         * @param supportedCapabilities the set of capabilities to declare
         * @return {@code this}
         */
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
        AnthropicChatRequestParameters parameters = (AnthropicChatRequestParameters) chatRequest.parameters();
        validate(parameters);

        AnthropicCreateMessageRequest anthropicRequest = createAnthropicRequest(
                chatRequest,
                toThinking(parameters.thinkingType(), parameters.thinkingBudgetTokens(), this.thinkingDisplay),
                getOrDefault(parameters.sendThinking(), true),
                getOrDefault(parameters.midConversationSystemMessages(), false),
                getOrDefault(parameters.cacheSystemMessages(), false) ? EPHEMERAL : NO_CACHE,
                getOrDefault(parameters.cacheTools(), false) ? EPHEMERAL : NO_CACHE,
                false,
                parameters.toolChoiceName(),
                parameters.disableParallelToolUse(),
                this.serverTools,
                this.toolMetadataKeysToSend,
                parameters.userId(),
                this.skills,
                this.customParameters,
                this.strictTools);

        ParsedAndRawResponse response =
                withRetryMappingExceptions(() -> client.createMessageWithRawResponse(anthropicRequest), maxRetries);

        boolean returnThinking = getOrDefault(parameters.returnThinking(), false);
        return createChatResponse(response, returnThinking);
    }

    private ChatResponse createChatResponse(ParsedAndRawResponse parsedAndRawResponse, boolean returnThinking) {
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

    static AnthropicThinking toThinking(String thinkingType, Integer thinkingBudgetTokens, String thinkingDisplay) {
        if (thinkingType != null || thinkingBudgetTokens != null) {
            return AnthropicThinking.builder()
                    .type(thinkingType)
                    .budgetTokens(thinkingBudgetTokens)
                    .display(thinkingDisplay)
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
    public AnthropicChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }
}
