package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.*;

import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.vertexai.anthropic.internal.Constants;
import dev.langchain4j.model.vertexai.anthropic.internal.ValidationUtils;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicRequest;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import dev.langchain4j.model.vertexai.anthropic.internal.client.StreamingResponseHandler;
import dev.langchain4j.model.vertexai.anthropic.internal.client.VertexAiAnthropicClient;
import dev.langchain4j.model.vertexai.anthropic.internal.mapper.AnthropicRequestMapper;
import dev.langchain4j.model.vertexai.anthropic.internal.mapper.AnthropicResponseMapper;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Google Vertex AI Anthropic language model with a streaming chat completion interface.
 * Supports Claude models through Vertex AI's Model Garden.
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
        this.stopSequences = copy(builder.stopSequences);
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.enablePromptCaching = getOrDefault(builder.enablePromptCaching, false);
        this.listeners = builder.listeners != null ? List.copyOf(builder.listeners) : List.of();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        // Validate that JSON responses format is not used (not supported)
        ChatRequestParameters parameters = chatRequest.parameters();
        if (parameters.responseFormat() != null) {
            try {
                handler.onError(new dev.langchain4j.exception.UnsupportedFeatureException(
                        "JSON responses format is not supported by Vertex AI Anthropic"));
            } catch (Exception userException) {
                logger.warn("User's onError handler threw an exception, ignoring", userException);
            }
            return;
        }

        try {
            String requestModelName = determineRequestModelName(chatRequest.parameters());
            AnthropicRequest anthropicRequest = buildAnthropicRequest(chatRequest, requestModelName);

            logRequestIfEnabled(requestModelName, chatRequest.parameters().modelName(), anthropicRequest);

            client.generateContentStreaming(anthropicRequest, requestModelName,
                    createStreamingResponseHandler(handler));

        } catch (IOException e) {
            try {
                handler.onError(new RuntimeException("Failed to generate responses", e));
            } catch (Exception userException) {
                logger.warn("User's onError handler threw an exception, ignoring", userException);
            }
        } catch (Exception e) {
            try {
                handler.onError(e);
            } catch (Exception userException) {
                logger.warn("User's onError handler threw an exception, ignoring", userException);
            }
        }
    }

    private String determineRequestModelName(ChatRequestParameters parameters) {
        return getOrDefault(parameters.modelName(), modelName);
    }

    private AnthropicRequest buildAnthropicRequest(ChatRequest chatRequest, String requestModelName) {
        ChatRequestParameters parameters = chatRequest.parameters();
        List<ChatMessage> messages = chatRequest.messages();
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();

        return AnthropicRequestMapper.toRequest(
                requestModelName,
                messages,
                toolSpecifications,
                parameters.toolChoice(),
                parameters.maxOutputTokens() != null ? parameters.maxOutputTokens() : maxTokens,
                temperature,
                topP,
                topK,
                parameters.stopSequences() != null && !parameters.stopSequences().isEmpty()
                        ? parameters.stopSequences()
                        : stopSequences,
                enablePromptCaching);
    }

    private void logRequestIfEnabled(String requestModelName, String parameterModelName, AnthropicRequest anthropicRequest) {
        if (logRequests) {
            logger.debug(
                    "Using model name: {} (from parameters: {}, default: {})",
                    requestModelName,
                    parameterModelName,
                    modelName);
            logger.debug("Anthropic streaming request: {}", anthropicRequest);
        }
    }

    private StreamingResponseHandler createStreamingResponseHandler(StreamingChatResponseHandler handler) {
        return new StreamingResponseHandler() {
            private final StringBuilder currentText = new StringBuilder();
            private final List<dev.langchain4j.agent.tool.ToolExecutionRequest> toolCalls = new ArrayList<>();
            private AnthropicResponse fullResponse;

            @Override
            public void onResponse(AnthropicResponse response) {
                this.fullResponse = response;
                extractToolCallsFromResponse(response);
            }

            @Override
            public void onChunk(String jsonChunk) {
                try {
                    if (logResponses) {
                        logger.debug("Anthropic streaming chunk: {}", jsonChunk);
                    }

                    processStreamingChunk(jsonChunk, handler);
                } catch (Exception e) {
                    logger.error("Error processing streaming chunk", e);
                    try {
                        handler.onError(e);
                    } catch (Exception userException) {
                        logger.warn("User's onError handler threw an exception, ignoring", userException);
                    }
                }
            }

            @Override
            public void onComplete() {
                try {
                    sendCompleteToolCalls(handler);
                    sendCompleteResponse(handler);
                } catch (Exception e) {
                    try {
                        handler.onError(e);
                    } catch (Exception userException) {
                        logger.warn("User's onError handler threw an exception, ignoring", userException);
                    }
                }
            }

            @Override
            public void onError(Throwable error) {
                try {
                    handler.onError(error);
                } catch (Exception userException) {
                    logger.warn("User's onError handler threw an exception, ignoring", userException);
                }
            }

            private void extractToolCallsFromResponse(AnthropicResponse response) {
                if (response.content != null) {
                    logger.debug("Processing {} content blocks from responses", response.content.size());
                    for (dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicContent content : response.content) {
                        logger.debug("Content block: type={}, name={}, id={}", content.type, content.name, content.id);
                        if (isToolUseContent(content)) {
                            processToolContent(content);
                        }
                    }
                    logger.debug("Total tool calls extracted: {}", toolCalls.size());
                }
            }

            private boolean isToolUseContent(dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicContent content) {
                return Constants.TOOL_USE_CONTENT_TYPE.equals(content.type) && content.name != null;
            }

            private void processToolContent(dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicContent content) {
                try {
                    String arguments = serializeToolArguments(content.input);
                    dev.langchain4j.agent.tool.ToolExecutionRequest toolRequest =
                            dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                    .id(content.id)
                                    .name(content.name)
                                    .arguments(arguments)
                                    .build();
                    toolCalls.add(toolRequest);
                    logger.debug("Added tool call: {}", toolRequest);
                } catch (Exception e) {
                    logger.warn("Failed to serialize tool arguments for {}: {}", content.name, e.getMessage());
                }
            }

            private String serializeToolArguments(Object input) throws com.fasterxml.jackson.core.JsonProcessingException {
                if (input == null) {
                    return "{}";
                }
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                return mapper.writeValueAsString(input);
            }

            private void processStreamingChunk(String jsonChunk, StreamingChatResponseHandler handler) {
                if (jsonChunk.contains("\"type\":\"content_block_delta\"")) {
                    handleTextDelta(jsonChunk, handler);
                } else if (jsonChunk.contains("\"type\":\"content_block_start\"")) {
                    handleToolCallStart(jsonChunk);
                } else if (jsonChunk.contains("\"type\":\"content_block_stop\"")) {
                    handleContentBlockStop(jsonChunk);
                }
            }

            private void handleTextDelta(String jsonChunk, StreamingChatResponseHandler handler) {
                String textDelta = extractTextDelta(jsonChunk);
                if (textDelta != null && !textDelta.isEmpty()) {
                    currentText.append(textDelta);
                    try {
                        handler.onPartialResponse(textDelta);
                    } catch (Exception userException) {
                        handler.onError(userException);
                    }
                }
            }

            private String extractTextDelta(String jsonChunk) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = 
                            new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(jsonChunk);
                    
                    com.fasterxml.jackson.databind.JsonNode deltaNode = rootNode.get("delta");
                    if (deltaNode != null && !deltaNode.isNull()) {
                        com.fasterxml.jackson.databind.JsonNode textNode = deltaNode.get("text");
                        if (textNode != null && !textNode.isNull() && textNode.isTextual()) {
                            return textNode.asText();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to extract text delta from chunk: {}", jsonChunk, e);
                }
                return null;
            }

            private void handleToolCallStart(String jsonChunk) {
                if (Boolean.TRUE.equals(logResponses)) {
                    logger.debug("Tool call started in chunk: {}", jsonChunk);
                }
            }

            private void handleContentBlockStop(String jsonChunk) {
                if (Boolean.TRUE.equals(logResponses)) {
                    logger.debug("Content block stopped in chunk: {}", jsonChunk);
                }
            }

            private void sendCompleteToolCalls(StreamingChatResponseHandler handler) {
                if (!toolCalls.isEmpty()) {
                    for (int i = 0; i < toolCalls.size(); i++) {
                        dev.langchain4j.agent.tool.ToolExecutionRequest toolRequest = toolCalls.get(i);
                        dev.langchain4j.model.chat.response.CompleteToolCall completeToolCall =
                                new dev.langchain4j.model.chat.response.CompleteToolCall(i, toolRequest);
                        logger.debug("Calling onCompleteToolCall for index {}: {}", i, toolRequest);
                        handler.onCompleteToolCall(completeToolCall);
                    }
                }
            }

            private void sendCompleteResponse(StreamingChatResponseHandler handler) {
                if (fullResponse != null) {
                    sendMappedResponse(handler);
                } else {
                    sendFallbackResponse(handler);
                }
            }

            private void sendMappedResponse(StreamingChatResponseHandler handler) {
                ChatResponse chatResponse = AnthropicResponseMapper.toChatResponse(fullResponse);
                logger.debug(
                        "ChatResponse from mapper: toolExecutionRequests.size()={}",
                        chatResponse.aiMessage().toolExecutionRequests().size());
                logger.debug(
                        "About to call onCompleteResponse with: {}",
                        chatResponse.aiMessage().toolExecutionRequests());
                handler.onCompleteResponse(chatResponse);
            }

            private void sendFallbackResponse(StreamingChatResponseHandler handler) {
                AiMessage.Builder aiMessageBuilder = AiMessage.builder().text(currentText.toString());
                if (!toolCalls.isEmpty()) {
                    aiMessageBuilder.toolExecutionRequests(toolCalls);
                }

                ChatResponse fallbackResponse = ChatResponse.builder()
                        .aiMessage(aiMessageBuilder.build())
                        .tokenUsage(new TokenUsage(currentText.length() / 4, currentText.length() / 4))
                        .finishReason(dev.langchain4j.model.output.FinishReason.STOP)
                        .build();

                handler.onCompleteResponse(fallbackResponse);
            }
        };
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
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
