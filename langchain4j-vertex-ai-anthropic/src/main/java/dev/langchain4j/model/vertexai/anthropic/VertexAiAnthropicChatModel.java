package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC;

import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.vertexai.anthropic.internal.ValidationUtils;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicRequest;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import dev.langchain4j.model.vertexai.anthropic.internal.client.VertexAiAnthropicClient;
import dev.langchain4j.model.vertexai.anthropic.internal.mapper.AnthropicRequestMapper;
import dev.langchain4j.model.vertexai.anthropic.internal.mapper.AnthropicResponseMapper;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Google Vertex AI Anthropic language model with a chat completion interface.
 * Supports Claude models through Vertex AI's Model Garden.
 * <br>
 * Please follow these steps before using this model:
 * <br>
 * 1. <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#local-developmenttesting">Authentication</a>
 * <br>
 * When developing locally, you can use one of:
 * <br>
 * a) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#local-developmenttesting">Google Cloud SDK</a>
 * <br>
 * b) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#using-a-service-account-recommended">Service account</a>
 * When using service account, ensure that <code>GOOGLE_APPLICATION_CREDENTIALS</code> environment variable points to your JSON service account key.
 * <br>
 * 2. <a href="https://cloud.google.com/vertex-ai/docs/model-garden/explore-models">Enable Vertex AI Model Garden</a>
 * <br>
 * 3. Request access to Claude models in Vertex AI Model Garden
 * <br>
 * 4. Model names must include version suffixes. Try one of these formats:
 * <br>
 * - "claude-3-5-sonnet-v2@20241022" (Claude 3.5 Sonnet v2)
 * - "claude-3-5-haiku@20241022" (Claude 3.5 Haiku)
 * - "claude-3-5-sonnet@20240620" (Claude 3.5 Sonnet)
 * - "claude-3-opus@20240229" (Claude 3 Opus)
 * <br>
 * Note: Model availability depends on your Google Cloud project's access to Anthropic models in Vertex AI Model Garden.
 */
public class VertexAiAnthropicChatModel implements ChatModel, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(VertexAiAnthropicChatModel.class);

    private final VertexAiAnthropicClient client;
    private final String modelName;
    private final Integer maxTokens;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final List<String> stopSequences;
    private final Boolean logRequests;
    private final Boolean logResponses;
    private final Boolean enablePromptCaching;
    private final List<ChatModelListener> listeners;

    public VertexAiAnthropicChatModel(VertexAiAnthropicChatModelBuilder builder) {
        this.client = new VertexAiAnthropicClient(
                ensureNotBlank(builder.project, "project"),
                ensureNotBlank(builder.location, "location"),
                ensureNotBlank(builder.modelName, "modelName"),
                builder.credentials);
        this.modelName = builder.modelName;
        this.maxTokens = ValidationUtils.validateMaxTokens(builder.maxTokens);
        this.temperature = ValidationUtils.validateTemperature(builder.temperature);
        this.topP = ValidationUtils.validateTopP(builder.topP);
        this.topK = ValidationUtils.validateTopK(builder.topK);
        this.stopSequences = builder.stopSequences;
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.enablePromptCaching = getOrDefault(builder.enablePromptCaching, false);
        this.listeners = builder.listeners != null ? List.copyOf(builder.listeners) : List.of();
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();

        List<ChatMessage> messages = chatRequest.messages();
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();

        ChatModelRequestContext requestContext =
                new ChatModelRequestContext(chatRequest, provider(), new ConcurrentHashMap<>());

        ConcurrentHashMap<Object, Object> attributes = new ConcurrentHashMap<>();
        requestContext.attributes().putAll(attributes);

        notifyListenersOnRequest(requestContext);

        try {
            AnthropicRequest anthropicRequest = AnthropicRequestMapper.toRequest(
                    modelName,
                    messages,
                    toolSpecifications,
                    parameters.toolChoice(),
                    parameters.maxOutputTokens() != null ? parameters.maxOutputTokens() : maxTokens,
                    temperature,
                    topP,
                    topK,
                    parameters.stopSequences() != null
                                    && !parameters.stopSequences().isEmpty()
                            ? parameters.stopSequences()
                            : stopSequences,
                    enablePromptCaching);

            if (logRequests) {
                logger.debug("Anthropic request: {}", anthropicRequest);
            }

            AnthropicResponse anthropicResponse = client.generateContent(anthropicRequest);

            if (logResponses) {
                logger.debug("Anthropic response: {}", anthropicResponse);
            }

            ChatResponse chatResponse = AnthropicResponseMapper.toChatResponse(anthropicResponse);

            ChatModelResponseContext responseContext =
                    new ChatModelResponseContext(chatResponse, chatRequest, provider(), attributes);

            notifyListenersOnResponse(responseContext);

            return chatResponse;

        } catch (Exception e) {
            ChatModelErrorContext errorContext = new ChatModelErrorContext(e, chatRequest, provider(), attributes);

            notifyListenersOnError(errorContext);

            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException("Failed to generate response", e);
            }
        }
    }

    @Override
    public ModelProvider provider() {
        return GOOGLE_VERTEX_AI_ANTHROPIC;
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    private void notifyListenersOnRequest(ChatModelRequestContext requestContext) {
        ValidationUtils.notifyListenersOnRequest(listeners, requestContext);
    }

    private void notifyListenersOnResponse(ChatModelResponseContext responseContext) {
        ValidationUtils.notifyListenersOnResponse(listeners, responseContext);
    }

    private void notifyListenersOnError(ChatModelErrorContext errorContext) {
        ValidationUtils.notifyListenersOnError(listeners, errorContext);
    }

    public static VertexAiAnthropicChatModelBuilder builder() {
        return new VertexAiAnthropicChatModelBuilder();
    }

    public static class VertexAiAnthropicChatModelBuilder {
        private String project;
        private String location;
        private String modelName;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private List<String> stopSequences;
        private Boolean logRequests;
        private Boolean logResponses;
        private Boolean enablePromptCaching;
        private List<ChatModelListener> listeners;
        private GoogleCredentials credentials;

        public VertexAiAnthropicChatModelBuilder project(String project) {
            this.project = project;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder location(String location) {
            this.location = location;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public VertexAiAnthropicChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Enables prompt caching for Claude models. When enabled, the model will automatically
         * cache prompts to reduce latency and costs for repeated similar requests.
         * Only supported by Claude models that support prompt caching.
         *
         * @param enablePromptCaching whether to enable prompt caching
         * @return this builder
         */
        public VertexAiAnthropicChatModelBuilder enablePromptCaching(Boolean enablePromptCaching) {
            this.enablePromptCaching = enablePromptCaching;
            return this;
        }

        /**
         * Sets the Google credentials to use for authentication.
         * If not provided, the client will use Application Default Credentials.
         *
         * @param credentials the Google credentials to use
         * @return this builder
         */
        public VertexAiAnthropicChatModelBuilder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public VertexAiAnthropicChatModel build() {
            return new VertexAiAnthropicChatModel(this);
        }
    }
}
