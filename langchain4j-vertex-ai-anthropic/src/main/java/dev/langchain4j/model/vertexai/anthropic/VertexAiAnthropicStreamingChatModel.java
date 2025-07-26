package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.*;

import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.vertexai.anthropic.internal.Constants;
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
 * Represents a Google Vertex AI Anthropic language model with a streaming chat completion interface.
 * Supports Claude models through Vertex AI's Model Garden.
 * <br>
 * Note: This is a simplified streaming implementation that currently provides the full response
 * as a single chunk. Full streaming support with incremental tokens will be added in future versions.
 */
public class VertexAiAnthropicStreamingChatModel implements StreamingChatModel, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(VertexAiAnthropicStreamingChatModel.class);

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

    public VertexAiAnthropicStreamingChatModel(VertexAiAnthropicStreamingChatModelBuilder builder) {
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
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());

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
                    maxTokens,
                    temperature,
                    topP,
                    topK,
                    stopSequences,
                    enablePromptCaching);

            if (logRequests) {
                logger.debug("Anthropic request: {}", anthropicRequest);
            }

            AnthropicResponse anthropicResponse = client.generateContent(anthropicRequest);

            if (logResponses) {
                logger.debug("Anthropic response: {}", anthropicResponse);
            }

            // Convert to streaming format - send as one chunk for now
            if (anthropicResponse.content != null && !anthropicResponse.content.isEmpty()) {
                StringBuilder fullResponse = new StringBuilder();
                anthropicResponse.content.forEach(content -> {
                    if (Constants.TEXT_CONTENT_TYPE.equals(content.type) && content.text != null) {
                        fullResponse.append(content.text);
                    }
                });

                String responseText = fullResponse.toString();
                if (!responseText.isEmpty()) {
                    handler.onPartialResponse(responseText);
                }
            }

            // Send complete response
            ChatResponse chatResponse = AnthropicResponseMapper.toChatResponse(anthropicResponse);
            handler.onCompleteResponse(chatResponse);

            // Notify listeners
            ChatModelResponseContext responseContext =
                    new ChatModelResponseContext(chatResponse, chatRequest, provider(), attributes);

            notifyListenersOnResponse(responseContext);

        } catch (Exception e) {
            ChatModelErrorContext errorContext = new ChatModelErrorContext(e, chatRequest, provider(), attributes);

            notifyListenersOnError(errorContext);

            // This is usually handled by the StreamingChatResponseHandler but we can also log
            logger.error("Error in streaming chat", e);
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

    public static VertexAiAnthropicStreamingChatModelBuilder builder() {
        return new VertexAiAnthropicStreamingChatModelBuilder();
    }

    public static class VertexAiAnthropicStreamingChatModelBuilder {
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

        public VertexAiAnthropicStreamingChatModelBuilder project(String project) {
            this.project = project;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder location(String location) {
            this.location = location;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public VertexAiAnthropicStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
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
        public VertexAiAnthropicStreamingChatModelBuilder enablePromptCaching(Boolean enablePromptCaching) {
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
        public VertexAiAnthropicStreamingChatModelBuilder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public VertexAiAnthropicStreamingChatModel build() {
            return new VertexAiAnthropicStreamingChatModel(this);
        }
    }
}
