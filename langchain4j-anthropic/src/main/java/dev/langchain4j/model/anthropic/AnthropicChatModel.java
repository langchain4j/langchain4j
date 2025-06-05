package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.ANTHROPIC;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createErrorContext;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createListenerRequest;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.validate;
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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
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
public class AnthropicChatModel implements ChatModel {

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
    private final ChatRequestParameters defaultRequestParameters;

    private AnthropicChatModel(AnthropicChatModelBuilder builder) {
        this.client = AnthropicClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(builder.apiKey)
                .version(getOrDefault(builder.version, "2023-06-01"))
                .beta(builder.beta)
                .timeout(getOrDefault(builder.timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.maxTokens = getOrDefault(builder.maxTokens, 1024);
        this.stopSequences = builder.stopSequences;
        this.cacheSystemMessages = getOrDefault(builder.cacheSystemMessages, false);
        this.cacheTools = getOrDefault(builder.cacheTools, false);
        this.thinkingType = builder.thinkingType;
        this.thinkingBudgetTokens = builder.thinkingBudgetTokens;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.listeners = builder.listeners == null ? emptyList() : new ArrayList<>(builder.listeners);
        this.defaultRequestParameters = getOrDefault(builder.defaultRequestParameters, DefaultChatRequestParameters.DEFAULT_INSTANCE);
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
        private ChatRequestParameters defaultRequestParameters;

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
            return new AnthropicChatModel(this);
        }

        public AnthropicChatModelBuilder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        validate(chatRequest.parameters());
        return generate(chatRequest);
    }

    private ChatResponse generate(ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        List<ChatMessage> sanitizedMessages = sanitizeMessages(messages);
        List<AnthropicTextContent> systemPrompt =
                toAnthropicSystemPrompt(messages, cacheSystemMessages ? EPHEMERAL : NO_CACHE);

        List<ToolSpecification> toolSpecifications = chatRequest.parameters().toolSpecifications();

        AnthropicCreateMessageRequest request = AnthropicCreateMessageRequest.builder()
                .model(getOrDefault(chatRequest.modelName(), modelName))
                .messages(toAnthropicMessages(sanitizedMessages))
                .system(systemPrompt)
                .maxTokens(getOrDefault(chatRequest.maxOutputTokens(), maxTokens))
                .stopSequences(getOrDefault(chatRequest.stopSequences(), stopSequences))
                .stream(false)
                .temperature(getOrDefault(chatRequest.temperature(), temperature))
                .topP(getOrDefault(chatRequest.topP(), topP))
                .topK(getOrDefault(chatRequest.topK(), topK))
                .tools(toAnthropicTools(toolSpecifications, cacheTools ? EPHEMERAL : NO_CACHE))
                .thinking(toThinking(thinkingType, thinkingBudgetTokens))
                .responseFormat(chatRequest.parameters().responseFormat())
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

            ChatModelResponseContext responseContext =
                    new ChatModelResponseContext(createChatResponse(response), listenerRequest, provider(), attributes);

            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return createChatResponse(response);
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

    private static ChatResponse createChatResponse(AnthropicCreateMessageResponse response) {
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .id(response.id)
                .modelName(response.model)
                .tokenUsage(toTokenUsage(response.usage))
                .finishReason(toFinishReason(response.stopReason))
                .build();

        return ChatResponse.builder()
                .aiMessage(toAiMessage(response.content))
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
}
