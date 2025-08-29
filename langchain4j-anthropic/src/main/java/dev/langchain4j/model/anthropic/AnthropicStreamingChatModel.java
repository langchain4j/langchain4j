package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ModelProvider.ANTHROPIC;
import static dev.langchain4j.model.anthropic.AnthropicChatModel.toThinking;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createAnthropicRequest;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.validate;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.EPHEMERAL;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.NO_CACHE;
import static java.util.Arrays.asList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.anthropic.internal.client.AnthropicCreateMessageOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;

/**
 * Represents an Anthropic language model with a Messages (chat) API.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <br>
 * More details are available <a href="https://docs.anthropic.com/claude/reference/messages_post">here</a>
 * and <a href="https://docs.anthropic.com/claude/reference/messages-streaming">here</a>.
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
public class AnthropicStreamingChatModel implements StreamingChatModel {

    private final AnthropicClient client;
    private final boolean cacheSystemMessages;
    private final boolean cacheTools;
    private final String thinkingType;
    private final Integer thinkingBudgetTokens;
    private final boolean returnThinking;
    private final boolean sendThinking;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;

    /**
     * Constructs an instance of an {@code AnthropicStreamingChatModel} with the specified parameters.
     */
    public AnthropicStreamingChatModel(AnthropicStreamingChatModelBuilder builder) {
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

        ChatRequestParameters commonParameters = DefaultChatRequestParameters.EMPTY;

        this.defaultRequestParameters = DefaultChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, getOrDefault(commonParameters.maxOutputTokens(), 1024)))
                .stopSequences(getOrDefault(builder.stopSequences, commonParameters.stopSequences()))
                .toolSpecifications(getOrDefault(builder.toolSpecifications, commonParameters.toolSpecifications()))
                .toolChoice(commonParameters.toolChoice())
                .build();

        this.cacheSystemMessages = getOrDefault(builder.cacheSystemMessages, false);
        this.cacheTools = getOrDefault(builder.cacheTools, false);
        this.thinkingType = builder.thinkingType;
        this.thinkingBudgetTokens = builder.thinkingBudgetTokens;
        this.returnThinking = getOrDefault(builder.returnThinking, false);
        this.sendThinking = getOrDefault(builder.sendThinking, true);
        this.listeners = copy(builder.listeners);
    }

    public static AnthropicStreamingChatModelBuilder builder() {
        return new AnthropicStreamingChatModelBuilder();
    }

    public static class AnthropicStreamingChatModelBuilder {

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
        private List<ToolSpecification> toolSpecifications;
        private Boolean cacheSystemMessages;
        private Boolean cacheTools;
        private String thinkingType;
        private Integer thinkingBudgetTokens;
        private Boolean returnThinking;
        private Boolean sendThinking;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private List<ChatModelListener> listeners;

        public AnthropicStreamingChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public AnthropicStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public AnthropicStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public AnthropicStreamingChatModelBuilder version(String version) {
            this.version = version;
            return this;
        }

        public AnthropicStreamingChatModelBuilder beta(String beta) {
            this.beta = beta;
            return this;
        }

        public AnthropicStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AnthropicStreamingChatModelBuilder modelName(AnthropicChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public AnthropicStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public AnthropicStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public AnthropicStreamingChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public AnthropicStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public AnthropicStreamingChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public AnthropicStreamingChatModelBuilder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public AnthropicStreamingChatModelBuilder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public AnthropicStreamingChatModelBuilder cacheSystemMessages(Boolean cacheSystemMessages) {
            this.cacheSystemMessages = cacheSystemMessages;
            return this;
        }

        public AnthropicStreamingChatModelBuilder cacheTools(Boolean cacheTools) {
            this.cacheTools = cacheTools;
            return this;
        }

        /**
         * Enables <a href="https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking">thinking</a>.
         */
        public AnthropicStreamingChatModelBuilder thinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        /**
         * Configures <a href="https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking">thinking</a>.
         */
        public AnthropicStreamingChatModelBuilder thinkingBudgetTokens(Integer thinkingBudgetTokens) {
            this.thinkingBudgetTokens = thinkingBudgetTokens;
            return this;
        }

        /**
         * Controls whether to return thinking/reasoning text (if available) inside {@link AiMessage#thinking()}
         * and whether to invoke the {@link StreamingChatResponseHandler#onPartialThinking(PartialThinking)} callback.
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
        public AnthropicStreamingChatModelBuilder returnThinking(Boolean returnThinking) {
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
        public AnthropicStreamingChatModelBuilder sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            return this;
        }

        public AnthropicStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public AnthropicStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public AnthropicStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public AnthropicStreamingChatModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public AnthropicStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public AnthropicStreamingChatModel build() {
            return new AnthropicStreamingChatModel(this);
        }
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ensureNotNull(handler, "handler");
        validate(chatRequest.parameters());
        AnthropicCreateMessageRequest anthropicRequest = createAnthropicRequest(chatRequest,
                toThinking(thinkingType, thinkingBudgetTokens),
                sendThinking,
                cacheSystemMessages ? EPHEMERAL : NO_CACHE,
                cacheTools ? EPHEMERAL : NO_CACHE,
                true);
        client.createMessage(anthropicRequest, new AnthropicCreateMessageOptions(returnThinking), handler);
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
}
