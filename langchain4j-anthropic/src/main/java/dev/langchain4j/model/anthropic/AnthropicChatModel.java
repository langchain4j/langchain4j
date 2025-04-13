package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.ANTHROPIC;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createErrorContext;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createListenerRequest;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createListenerResponse;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.EPHEMERAL;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.NO_CACHE;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAiMessage;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSystemPrompt;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicTools;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toTokenUsage;
import static dev.langchain4j.model.anthropic.internal.sanitizer.MessageSanitizer.sanitizeMessages;
import static java.util.Collections.emptyList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Sanitization is performed on the {@link ChatMessage}s provided to conform to Anthropic API requirements. This process
 * includes verifying that the first message is a {@link UserMessage} and removing any consecutive {@link UserMessage}s.
 * Any messages removed during sanitization are logged as warnings and not submitted to the API.
 * <br>
 * <br>
 * Supports caching {@link SystemMessage}s and {@link ToolSpecification}s.
 */
public class AnthropicChatModel implements ChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(AnthropicChatModel.class);

    private final AnthropicClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final int maxTokens;
    private final List<String> stopSequences;
    private final boolean cacheSystemMessages;
    private final boolean cacheTools;
    private final String thinkingType;
    private final Integer thinkingBudgetTokens;
    private final int maxRetries;
    private final List<ChatModelListener> listeners;

    /**
     * Constructs an instance of an {@code AnthropicChatModel} with the specified parameters.
     *
     * @param baseUrl             The base URL of the Anthropic API. Default: "https://api.anthropic.com/v1/"
     * @param apiKey              The API key for authentication with the Anthropic API.
     * @param version             The value of the "anthropic-version" HTTP header. Default: "2023-06-01"
     * @param beta                The value of the "anthropic-beta" HTTP header.
     * @param modelName           The name of the Anthropic model to use.
     * @param temperature         The temperature
     * @param topP                The top-P
     * @param topK                The top-K
     * @param maxTokens           The maximum number of tokens to generate. Default: 1024
     * @param stopSequences       The custom text sequences that will cause the model to stop generating
     * @param cacheSystemMessages If true, it will add cache_control block to all system messages. Default: false
     * @param cacheTools          If true, it will add cache_control block to all tools. Default: false
     * @param timeout             The timeout for API requests. Default: 60 seconds
     * @param maxRetries          The maximum number of retries for API requests. Default: 3
     * @param logRequests         Whether to log the content of API requests using SLF4J. Default: false
     * @param logResponses        Whether to log the content of API responses using SLF4J. Default: false
     * @param listeners           A list of {@link ChatModelListener} instances to be notified.
     */
    private AnthropicChatModel(
            String baseUrl,
            String apiKey,
            String version,
            String beta,
            String modelName,
            Double temperature,
            Double topP,
            Integer topK,
            Integer maxTokens,
            List<String> stopSequences,
            Boolean cacheSystemMessages,
            Boolean cacheTools,
            String thinkingType,
            Integer thinkingBudgetTokens,
            Duration timeout,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses,
            List<ChatModelListener> listeners) {
        this.client = AnthropicClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(apiKey)
                .version(getOrDefault(version, "2023-06-01"))
                .beta(beta)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxTokens = getOrDefault(maxTokens, 1024);
        this.stopSequences = stopSequences;
        this.cacheSystemMessages = getOrDefault(cacheSystemMessages, false);
        this.cacheTools = getOrDefault(cacheTools, false);
        this.thinkingType = thinkingType;
        this.thinkingBudgetTokens = thinkingBudgetTokens;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    public static AnthropicChatModelBuilder builder() {
        return new AnthropicChatModelBuilder();
    }

    public static class AnthropicChatModelBuilder {

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
        private Boolean cacheSystemMessages;
        private Boolean cacheTools;
        private String thinkingType;
        private Integer thinkingBudgetTokens;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;

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

        public AnthropicChatModelBuilder cacheSystemMessages(Boolean cacheSystemMessages) {
            this.cacheSystemMessages = cacheSystemMessages;
            return this;
        }

        public AnthropicChatModelBuilder cacheTools(Boolean cacheTools) {
            this.cacheTools = cacheTools;
            return this;
        }

        public AnthropicChatModelBuilder thinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        public AnthropicChatModelBuilder thinkingBudgetTokens(Integer thinkingBudgetTokens) {
            this.thinkingBudgetTokens = thinkingBudgetTokens;
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

        public AnthropicChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public AnthropicChatModel build() {
            return new AnthropicChatModel(
                    baseUrl,
                    apiKey,
                    version,
                    beta,
                    modelName,
                    temperature,
                    topP,
                    topK,
                    maxTokens,
                    stopSequences,
                    cacheSystemMessages,
                    cacheTools,
                    thinkingType,
                    thinkingBudgetTokens,
                    timeout,
                    maxRetries,
                    logRequests,
                    logResponses,
                    listeners);
        }
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.toolChoice());
        ChatRequestValidator.validate(parameters.responseFormat());

        Response<AiMessage> response = generate(chatRequest.messages(), parameters.toolSpecifications());

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {

        List<ChatMessage> sanitizedMessages = sanitizeMessages(messages);
        List<AnthropicTextContent> systemPrompt =
                toAnthropicSystemPrompt(messages, cacheSystemMessages ? EPHEMERAL : NO_CACHE);

        AnthropicCreateMessageRequest request = AnthropicCreateMessageRequest.builder()
                .model(modelName)
                .messages(toAnthropicMessages(sanitizedMessages))
                .system(systemPrompt)
                .maxTokens(maxTokens)
                .stopSequences(stopSequences)
                .stream(false)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .tools(toAnthropicTools(toolSpecifications, cacheTools ? EPHEMERAL : NO_CACHE))
                .thinking(toThinking(thinkingType, thinkingBudgetTokens))
                .build();

        ChatRequest listenerRequest = createListenerRequest(request, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(listenerRequest, provider(), attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        try {
            AnthropicCreateMessageResponse response =
                    withRetryMappingExceptions(() -> client.createMessage(request), maxRetries);
            Response<AiMessage> responseMessage = Response.from(
                    toAiMessage(response.content), toTokenUsage(response.usage), toFinishReason(response.stopReason));

            ChatResponse listenerResponse = createListenerResponse(response.id, response.model, responseMessage);
            ChatModelResponseContext responseContext =
                    new ChatModelResponseContext(listenerResponse, listenerRequest, provider(), attributes);

            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return Response.from(
                    toAiMessage(response.content), toTokenUsage(response.usage), toFinishReason(response.stopReason));
        } catch (RuntimeException e) {
            ChatModelErrorContext errorContext = createErrorContext(e, listenerRequest, provider(), attributes);

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    log.warn("Exception while calling model listener", e2);
                }
            });

            throw e;
        }
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
}
