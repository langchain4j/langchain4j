package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseErrorEvent;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseFunctionCallArgumentsDeltaEvent;
import com.openai.models.responses.ResponseFunctionCallArgumentsDoneEvent;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInProgressEvent;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseIncompleteEvent;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseOutputItemAddedEvent;
import com.openai.models.responses.ResponseOutputItemDoneEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.models.responses.ToolChoiceOptions;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StreamingChatModel implementation using the official OpenAI Java client for the Responses API.
 */
public class OpenAiOfficialResponsesStreamingChatModel implements StreamingChatModel {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiOfficialResponsesStreamingChatModel.class);
    private static final String PROMPT_CACHE_RETENTION_FIELD = "prompt_cache_retention";

    private final OpenAIClient client;
    private final ExecutorService executorService;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Long maxOutputTokens;
    private final Long maxToolCalls;
    private final Boolean parallelToolCalls;
    private final String previousResponseId;
    private final Long topLogprobs;
    private final String truncation;
    private final List<String> include;
    private final String serviceTier;
    private final String safetyIdentifier;
    private final String promptCacheKey;
    private final String promptCacheRetention;
    private final String reasoningEffort;
    private final String textVerbosity;
    private final Boolean streamIncludeObfuscation;
    private final List<ChatModelListener> listeners;
    private final ResponseCancellationHandler cancellationHandler;
    private final Boolean strict;

    private OpenAiOfficialResponsesStreamingChatModel(Builder builder) {
        this.client = Objects.requireNonNull(builder.client, "client");
        this.executorService = Objects.requireNonNull(builder.executorService, "executorService");
        this.modelName = Objects.requireNonNull(builder.modelName, "modelName");
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.maxToolCalls = builder.maxToolCalls;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.previousResponseId = builder.previousResponseId;
        this.topLogprobs = builder.topLogprobs;
        this.truncation = builder.truncation;
        this.include = builder.include != null ? new ArrayList<>(builder.include) : null;
        this.serviceTier = builder.serviceTier;
        this.safetyIdentifier = builder.safetyIdentifier;
        this.promptCacheKey = builder.promptCacheKey;
        this.promptCacheRetention = builder.promptCacheRetention;
        this.reasoningEffort = builder.reasoningEffort;
        this.textVerbosity = builder.textVerbosity;
        this.streamIncludeObfuscation = builder.streamIncludeObfuscation;
        this.listeners = builder.listeners != null ? new ArrayList<>(builder.listeners) : new ArrayList<>();
        this.cancellationHandler = builder.cancellationHandler;
        this.strict = builder.strict != null ? builder.strict : true;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        // Determine the model name to use (request parameter overrides default)
        String effectiveModelName =
                chatRequest.parameters() != null && chatRequest.parameters().modelName() != null
                        ? chatRequest.parameters().modelName()
                        : modelName;

        logger.debug(
                "Starting doChat with model: {}, messages: {}, tools: {}",
                effectiveModelName,
                chatRequest.messages().size(),
                chatRequest.toolSpecifications() != null
                        ? chatRequest.toolSpecifications().size()
                        : 0);

        AtomicReference<String> responseIdRef = new AtomicReference<>();
        Future<?> cancellationMonitorFuture = null;

        try {
            logger.debug("Building ResponseCreateParams for model: {}", effectiveModelName);
            var paramsBuilder = ResponseCreateParams.builder()
                    .model(ResponsesModel.ofChat(ChatModel.of(effectiveModelName)))
                    .store(false);

            // Convert messages to ResponseInputItems
            var inputItems = new ArrayList<ResponseInputItem>();
            for (var msg : chatRequest.messages()) {
                inputItems.addAll(toResponseInputItems(msg));
            }
            paramsBuilder.inputOfResponse(inputItems);
            logger.debug(
                    "Converted {} messages to {} input items",
                    chatRequest.messages().size(),
                    inputItems.size());

            // Add optional parameters (request parameters override defaults)
            Double effectiveTemperature = chatRequest.temperature() != null ? chatRequest.temperature() : temperature;
            if (effectiveTemperature != null) {
                paramsBuilder.temperature(effectiveTemperature);
            }

            Double effectiveTopP = chatRequest.topP() != null ? chatRequest.topP() : topP;
            if (effectiveTopP != null) {
                paramsBuilder.topP(effectiveTopP);
            }

            Integer requestMaxOutputTokens = chatRequest.maxOutputTokens();
            Long effectiveMaxOutputTokens =
                    requestMaxOutputTokens != null ? (Long) requestMaxOutputTokens.longValue() : maxOutputTokens;
            if (effectiveMaxOutputTokens != null) {
                // Responses API requires minimum of 16 tokens
                long finalMaxOutputTokens = Math.max(effectiveMaxOutputTokens, 16L);
                paramsBuilder.maxOutputTokens(finalMaxOutputTokens);
            }
            if (maxToolCalls != null) {
                paramsBuilder.maxToolCalls(maxToolCalls);
            }
            if (parallelToolCalls != null) {
                paramsBuilder.parallelToolCalls(parallelToolCalls);
            }
            if (previousResponseId != null) {
                paramsBuilder.previousResponseId(previousResponseId);
            }
            if (topLogprobs != null) {
                paramsBuilder.topLogprobs(topLogprobs);
            }
            if (truncation != null && !truncation.isEmpty()) {
                paramsBuilder.truncation(ResponseCreateParams.Truncation.of(truncation));
            }
            if (include != null && !include.isEmpty()) {
                var includables = new ArrayList<ResponseIncludable>();
                for (var item : include) {
                    includables.add(ResponseIncludable.of(item));
                }
                paramsBuilder.include(includables);
            }
            if (serviceTier != null && !serviceTier.isEmpty()) {
                paramsBuilder.serviceTier(ResponseCreateParams.ServiceTier.of(serviceTier));
            }
            if (safetyIdentifier != null) {
                paramsBuilder.safetyIdentifier(safetyIdentifier);
            }
            if (promptCacheKey != null) {
                paramsBuilder.promptCacheKey(promptCacheKey);
            }
            if (promptCacheRetention != null) {
                paramsBuilder.putAdditionalBodyProperty(
                        PROMPT_CACHE_RETENTION_FIELD, JsonValue.from(promptCacheRetention));
            }
            if (reasoningEffort != null && !reasoningEffort.isEmpty()) {
                paramsBuilder.reasoning(Reasoning.builder()
                        .effort(ReasoningEffort.of(reasoningEffort))
                        .build());
            }
            if (streamIncludeObfuscation != null) {
                paramsBuilder.streamOptions(ResponseCreateParams.StreamOptions.builder()
                        .includeObfuscation(streamIncludeObfuscation)
                        .build());
            }

            // Add tools if present
            if (chatRequest.toolSpecifications() != null
                    && !chatRequest.toolSpecifications().isEmpty()) {
                for (var toolSpec : chatRequest.toolSpecifications()) {
                    paramsBuilder.addTool(toResponsesTool(toolSpec, strict));
                }

                // Add tool choice if specified
                if (chatRequest.toolChoice() != null) {
                    paramsBuilder.toolChoice(toResponsesToolChoice(chatRequest.toolChoice()));
                }
            }

            ResponseTextConfig textConfig = toResponseTextConfig(chatRequest.responseFormat(), strict, textVerbosity);
            if (textConfig != null) {
                paramsBuilder.text(textConfig);
            }

            // Start background cancellation monitoring if handler is available
            if (cancellationHandler != null) {
                cancellationMonitorFuture = cancellationHandler.startMonitoring(() -> {
                    String responseId = responseIdRef.get();
                    if (responseId != null) {
                        try {
                            logger.debug("Cancelling response: {}", responseId);
                            client.responses().cancel(responseId);
                        } catch (Exception e) {
                            logger.warn("Error cancelling response", e);
                        }
                    }
                });
            }

            var params = paramsBuilder.build();
            var eventHandler =
                    new ResponsesEventHandler(handler, cancellationHandler, responseIdRef, effectiveModelName);
            final var finalCancellationMonitorFuture = cancellationMonitorFuture;

            // The forEach call blocks, so it is submitted to the executor service to run asynchronously,
            // this is the only thread used.
            executorService.submit(() -> {
                try (var streamResponse = client.responses().createStreaming(params)) {
                    streamResponse.stream().forEach(eventHandler::handleEvent);
                } catch (CancellationException e) {
                    logger.debug("Stream cancelled by user");
                    safeOnError(handler, e);
                } catch (Exception e) {
                    logger.error("Exception in stream processing: {}", e.getMessage(), e);
                    safeOnError(handler, e);
                } finally {
                    if (finalCancellationMonitorFuture != null) {
                        finalCancellationMonitorFuture.cancel(false);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Exception in doChat: {}", e.getMessage(), e);
            safeOnError(handler, e);
            if (cancellationMonitorFuture != null) {
                cancellationMonitorFuture.cancel(false);
            }
        }
    }

    private static void safeOnError(StreamingChatResponseHandler handler, Throwable error) {
        try {
            handler.onError(error);
        } catch (Exception e) {
            logger.warn("Exception thrown by onError handler, ignoring", e);
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.builder()
                .modelName(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxOutputTokens(maxOutputTokens != null ? maxOutputTokens.intValue() : null)
                .build();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return Collections.unmodifiableList(listeners);
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    private static List<ResponseInputItem> toResponseInputItems(ChatMessage msg) {
        if (msg instanceof SystemMessage systemMessage) {
            return List.of(createTextMessage(EasyInputMessage.Role.SYSTEM, systemMessage.text()));
        } else if (msg instanceof UserMessage userMessage) {
            return List.of(createUserMessage(userMessage));
        } else if (msg instanceof AiMessage aiMessage) {
            var items = new ArrayList<ResponseInputItem>();

            // Add text message if present
            var text = aiMessage.text();
            if (text != null && !text.isEmpty()) {
                items.add(createTextMessage(EasyInputMessage.Role.ASSISTANT, text));
            }

            // Add function calls if present
            if (aiMessage.hasToolExecutionRequests()) {
                aiMessage.toolExecutionRequests().stream()
                        .map(toolRequest -> ResponseInputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
                                .callId(toolRequest.id())
                                .name(toolRequest.name())
                                .arguments(toolRequest.arguments())
                                .build()))
                        .forEach(items::add);
            }

            // If no text and no tool calls, return empty assistant message
            if (items.isEmpty()) {
                items.add(createTextMessage(EasyInputMessage.Role.ASSISTANT, ""));
            }

            return items;
        } else if (msg instanceof ToolExecutionResultMessage toolResultMessage) {
            // Tool execution result - convert to function call output
            return List.of(ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                    .callId(toolResultMessage.id())
                    .output(toolResultMessage.text())
                    .build()));
        } else {
            return List.of(createTextMessage(EasyInputMessage.Role.USER, msg.toString()));
        }
    }

    private static ResponseInputItem createTextMessage(EasyInputMessage.Role role, String text) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(EasyInputMessage.Content.ofTextInput(text))
                .build());
    }

    private static ResponseInputItem createUserMessage(UserMessage userMessage) {
        List<Content> contents = userMessage.contents();
        var contentList = new ArrayList<ResponseInputContent>();

        for (Content content : contents) {
            if (content instanceof TextContent textContent) {
                contentList.add(ResponseInputContent.ofInputText(
                        ResponseInputText.builder().text(textContent.text()).build()));
            } else if (content instanceof ImageContent imageContent) {
                Image image = imageContent.image();
                String imageUrl = buildImageUrl(image);
                contentList.add(ResponseInputContent.ofInputImage(ResponseInputImage.builder()
                        .imageUrl(imageUrl)
                        .detail(ResponseInputImage.Detail.AUTO)
                        .build()));
            }
        }

        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER)
                .content(EasyInputMessage.Content.ofResponseInputMessageContentList(contentList))
                .build());
    }

    private static String buildImageUrl(Image image) {
        if (image.url() != null) {
            return image.url().toString();
        } else if (image.base64Data() != null) {
            String mimeType = image.mimeType() != null ? image.mimeType() : "image/jpeg";
            return "data:" + mimeType + ";base64," + image.base64Data();
        } else {
            throw new IllegalArgumentException("Image must have either url or base64Data");
        }
    }

    private static FunctionTool toResponsesTool(ToolSpecification toolSpec, boolean strict) {
        try {
            var parametersBuilder = FunctionTool.Parameters.builder();
            if (toolSpec.parameters() != null) {
                toMap(toolSpec.parameters(), strict)
                        .forEach((key, value) -> parametersBuilder.putAdditionalProperty(key, JsonValue.from(value)));
            } else if (strict) {
                parametersBuilder
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(Collections.emptyMap()))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false));
            }
            return FunctionTool.builder()
                    .name(toolSpec.name())
                    .description(toolSpec.description())
                    .parameters(parametersBuilder.build())
                    .strict(strict)
                    .build();
        } catch (Exception e) {
            logger.error("Failed to convert tool specification: {}", toolSpec.name(), e);
            throw new RuntimeException("Failed to convert tool specification for tool: " + toolSpec.name(), e);
        }
    }

    private static ToolChoiceOptions toResponsesToolChoice(ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        return switch (toolChoice) {
            case AUTO -> ToolChoiceOptions.AUTO;
            case REQUIRED -> ToolChoiceOptions.REQUIRED;
            case NONE -> ToolChoiceOptions.NONE;
        };
    }

    private static ResponseTextConfig toResponseTextConfig(
            ResponseFormat responseFormat, Boolean strict, String textVerbosity) {
        ResponseTextConfig.Builder builder = null;

        if (responseFormat != null && responseFormat.type() != ResponseFormatType.TEXT) {
            builder = ResponseTextConfig.builder();
            JsonSchema jsonSchema = responseFormat.jsonSchema();
            if (jsonSchema == null) {
                builder.format(ResponseFormatTextConfig.ofJsonObject(
                        ResponseFormatJsonObject.builder().build()));
            } else {
                if (!(jsonSchema.rootElement() instanceof JsonObjectSchema
                        || jsonSchema.rootElement() instanceof JsonRawSchema)) {
                    throw new IllegalArgumentException(
                            "For OpenAI Responses API, the root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                                    + jsonSchema.rootElement().getClass());
                }

                Map<String, Object> schemaMap = toMap(jsonSchema.rootElement(), strict);
                ResponseFormatTextJsonSchemaConfig.Schema.Builder schemaBuilder =
                        ResponseFormatTextJsonSchemaConfig.Schema.builder();

                for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
                    schemaBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
                }

                ResponseFormatTextJsonSchemaConfig schemaConfig = ResponseFormatTextJsonSchemaConfig.builder()
                        .name(jsonSchema.name())
                        .schema(schemaBuilder.build())
                        .strict(strict)
                        .build();

                builder.format(ResponseFormatTextConfig.ofJsonSchema(schemaConfig));
            }
        }

        if (textVerbosity != null && !textVerbosity.isEmpty()) {
            if (builder == null) {
                builder = ResponseTextConfig.builder();
            }
            builder.verbosity(com.openai.models.responses.ResponseTextConfig.Verbosity.Companion.of(textVerbosity));
        }

        return builder != null ? builder.build() : null;
    }

    public static class Builder {
        private OpenAIClient client;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Long maxOutputTokens;
        private Long maxToolCalls;
        private Boolean parallelToolCalls;
        private String previousResponseId;
        private Long topLogprobs;
        private String truncation;
        private List<String> include;
        private String serviceTier;
        private String safetyIdentifier;
        private String promptCacheKey;
        private String promptCacheRetention;
        private String reasoningEffort;
        private String textVerbosity;
        private Boolean streamIncludeObfuscation;
        private List<ChatModelListener> listeners;
        private ResponseCancellationHandler cancellationHandler;
        private ExecutorService executorService;
        private Boolean strict;

        public Builder client(OpenAIClient client) {
            this.client = client;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxOutputTokens(Long maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder maxToolCalls(Long maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        public Builder topLogprobs(Long topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder truncation(String truncation) {
            this.truncation = truncation;
            return this;
        }

        public Builder include(List<String> include) {
            this.include = include;
            return this;
        }

        /**
         * When Enterprise Open AI subscription is used, service tier = "priority" puts requests into a
         * faster pool.
         */
        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder safetyIdentifier(String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        public Builder promptCacheKey(String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        public Builder promptCacheRetention(String promptCacheRetention) {
            this.promptCacheRetention = promptCacheRetention;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder textVerbosity(String textVerbosity) {
            this.textVerbosity = textVerbosity;
            return this;
        }

        public Builder streamIncludeObfuscation(Boolean streamIncludeObfuscation) {
            this.streamIncludeObfuscation = streamIncludeObfuscation;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder cancellationHandler(ResponseCancellationHandler cancellationHandler) {
            this.cancellationHandler = cancellationHandler;
            return this;
        }

        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Sets whether to use strict mode for function calling. Defaults to true.
         *
         * <p>When strict mode is enabled, the schema will include "additionalProperties": false and
         * "required" arrays with all property keys.
         */
        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        public OpenAiOfficialResponsesStreamingChatModel build() {
            return new OpenAiOfficialResponsesStreamingChatModel(this);
        }
    }

    /** Event handler for Responses API streaming. */
    private static class ResponsesEventHandler {
        private final StreamingChatResponseHandler handler;
        private final ResponseCancellationHandler cancellationHandler;
        private final AtomicReference<String> responseIdRef;
        private final String modelName;
        private final Map<String, ToolExecutionRequest.Builder> toolCallBuilders = new HashMap<>();
        private final Map<String, Integer> toolCallIndices = new HashMap<>();
        private final List<ToolExecutionRequest> completedToolCalls = new ArrayList<>();
        private final StringBuilder textBuilder = new StringBuilder();
        private OpenAiOfficialTokenUsage tokenUsage;
        private String responseId;
        private String finishReason;
        private int nextToolCallIndex = 0;

        ResponsesEventHandler(
                StreamingChatResponseHandler handler,
                ResponseCancellationHandler cancellationHandler,
                AtomicReference<String> responseIdRef,
                String modelName) {
            this.handler = handler;
            this.cancellationHandler = cancellationHandler;
            this.responseIdRef = responseIdRef;
            this.modelName = modelName;
        }

        void handleEvent(ResponseStreamEvent event) {
            if (cancellationHandler != null && cancellationHandler.isCancelled()) {
                throw new CancellationException("Request cancelled by user");
            }

            try {
                // TODO: process reasoning events, specifically ResponseReasoningSummaryTextDoneEvent and
                //   ResponseReasoningTextDoneEvent. It makes little sense to map them to onPartialThinking,
                //   need reasoning callbacks.
                if (event.isCreated()) {
                    handleCreated(event.asCreated());
                } else if (event.isInProgress()) {
                    handleInProgress(event.asInProgress());
                } else if (event.contentPartAdded().isPresent()) {
                    handleContentPartAdded(event.contentPartAdded().get());
                } else if (event.isOutputTextDelta()) {
                    handleOutputTextDelta(event.asOutputTextDelta());
                } else if (event.outputTextDone().isPresent()) {
                    handleOutputTextDone(event.outputTextDone().get());
                } else if (event.contentPartDone().isPresent()) {
                    handleContentPartDone(event.contentPartDone().get());
                } else if (event.isOutputItemAdded()) {
                    handleOutputItemAdded(event.asOutputItemAdded());
                } else if (event.isFunctionCallArgumentsDelta()) {
                    handleFunctionCallArgumentsDelta(event.asFunctionCallArgumentsDelta());
                } else if (event.isFunctionCallArgumentsDone()) {
                    handleFunctionCallArgumentsDone(event.asFunctionCallArgumentsDone());
                } else if (event.isOutputItemDone()) {
                    handleOutputItemDone(event.asOutputItemDone());
                } else if (event.isCompleted()) {
                    handleCompleted(event.asCompleted());
                } else if (event.isError()) {
                    handleError(event.asError());
                } else if (event.isFailed()) {
                    handleFailed(event.asFailed());
                } else if (event.isIncomplete()) {
                    handleIncomplete(event.asIncomplete());
                } else {
                    logger.warn(
                            "Unhandled event type: {}, event details: {}",
                            event.getClass().getName(),
                            event);
                }
            } catch (RuntimeException e) {
                logger.error("Error handling event: {}", e.getMessage(), e);
                // Re-throw without calling onError here - it will be called in the outer catch block
                throw e;
            } catch (Exception e) {
                logger.error("Error handling event: {}", e.getMessage(), e);
                // Re-throw without calling onError here - it will be called in the outer catch block
                throw new RuntimeException(e);
            }
        }

        private void handleCreated(ResponseCreatedEvent event) {
            this.responseId = event.response().id();
            responseIdRef.set(responseId);
        }

        private void handleInProgress(ResponseInProgressEvent event) {
            // No-op
        }

        private void handleContentPartAdded(Object event) {
            // No-op - just signals that a new content part is starting
        }

        private void handleOutputTextDelta(ResponseTextDeltaEvent event) {
            var delta = event.delta();
            if (!delta.isEmpty()) {
                textBuilder.append(delta);
                try {
                    handler.onPartialResponse(delta);
                } catch (Exception e) {
                    logger.debug("Exception from onPartialResponse, calling onError and continuing", e);
                    safeOnError(handler, e);
                }
            }
        }

        private void handleOutputTextDone(Object event) {
            // No-op - text is already accumulated in textBuilder
        }

        private void handleContentPartDone(Object event) {
            // No-op - signals that a content part is complete
        }

        private void handleOutputItemAdded(ResponseOutputItemAddedEvent event) {
            var item = event.item();
            if (item.isFunctionCall()) {
                var functionCall = item.asFunctionCall();
                var itemId = functionCall.id().orElse(null);
                if (itemId != null) {
                    toolCallBuilders.put(
                            itemId,
                            ToolExecutionRequest.builder()
                                    .id(functionCall.callId())
                                    .name(functionCall.name())
                                    .arguments(""));
                    toolCallIndices.put(itemId, nextToolCallIndex++);
                } else {
                    logger.warn("Function call missing item ID: {}", functionCall.callId());
                }
            }
        }

        private void handleFunctionCallArgumentsDelta(ResponseFunctionCallArgumentsDeltaEvent event) {
            // Delta events are ignored
        }

        private void handleFunctionCallArgumentsDone(ResponseFunctionCallArgumentsDoneEvent event) {
            var itemId = event.itemId();
            var builder = toolCallBuilders.remove(itemId);
            var index = toolCallIndices.remove(itemId);
            if (builder != null && index != null) {
                builder.arguments(event.arguments());
                ToolExecutionRequest toolExecutionRequest = builder.build();
                completedToolCalls.add(toolExecutionRequest);

                try {
                    handler.onCompleteToolCall(new CompleteToolCall(index, toolExecutionRequest));
                } catch (Exception e) {
                    logger.debug("Exception from onCompleteToolCall, calling onError", e);
                    safeOnError(handler, e);
                }
            } else {
                logger.warn("No builder for itemId in argumentsDone: {}", itemId);
            }
        }

        private void handleOutputItemDone(ResponseOutputItemDoneEvent event) {
            // No-op
        }

        private void handleCompleted(ResponseCompletedEvent event) {
            var response = event.response();

            // Extract status and map to finish reason
            response.status().ifPresent(status -> {
                this.finishReason = mapStatusToFinishReason(status.toString());
            });

            // Extract token usage and complete
            extractTokenUsageAndComplete(response);
        }

        private String mapStatusToFinishReason(String status) {
            if (status == null) {
                return null;
            }
            return switch (status) {
                case "completed" -> !completedToolCalls.isEmpty() ? "TOOL_EXECUTION" : "STOP";
                case "incomplete" -> "LENGTH";
                case "failed" -> "OTHER";
                default -> "OTHER";
            };
        }

        private void handleError(ResponseErrorEvent event) {
            var code = event.code().orElse("UNKNOWN");
            var message = event.message();
            logger.error("Response error: code={}, message={}", code, message);
            safeOnError(handler, new RuntimeException("Response error: " + message));
        }

        private void handleFailed(ResponseFailedEvent event) {
            var response = event.response();
            var error = response.error();
            logger.error("Response failed: {}", error);
            safeOnError(handler, new RuntimeException("Response failed: " + error));
        }

        private void handleIncomplete(ResponseIncompleteEvent event) {
            var response = event.response();
            var incompleteDetails = response.incompleteDetails();
            logger.debug("Response incomplete: {}", incompleteDetails);

            // Incomplete is not an error - it just means the response was cut off due to token limits
            // Treat it as a normal completion with finish reason LENGTH
            finishReason = "LENGTH";

            // Complete the response normally
            extractTokenUsageAndComplete(response);
        }

        private void extractTokenUsageAndComplete(com.openai.models.responses.Response response) {
            // Extract token usage
            response.usage().ifPresent(usage -> {
                var builder = OpenAiOfficialTokenUsage.builder()
                        .inputTokenCount((int) usage.inputTokens())
                        .outputTokenCount((int) usage.outputTokens())
                        .totalTokenCount((int) usage.totalTokens());

                var cachedTokens = usage.inputTokensDetails().cachedTokens();
                if (cachedTokens > 0) {
                    builder.inputTokensDetails(OpenAiOfficialTokenUsage.InputTokensDetails.builder()
                            .cachedTokens((int) cachedTokens)
                            .build());
                }

                tokenUsage = builder.build();
            });

            // Build final AI message
            var text = !textBuilder.isEmpty() ? textBuilder.toString() : null;
            var aiMessage = !completedToolCalls.isEmpty() && text != null
                    ? new AiMessage(text, completedToolCalls)
                    : !completedToolCalls.isEmpty()
                            ? AiMessage.from(completedToolCalls)
                            : new AiMessage(textBuilder.toString());

            // Build metadata
            var metadataBuilder =
                    OpenAiOfficialChatResponseMetadata.builder().id(responseId).modelName(modelName);

            if (finishReason != null) {
                metadataBuilder.finishReason(dev.langchain4j.model.output.FinishReason.valueOf(finishReason));
            }

            if (tokenUsage != null) {
                metadataBuilder.tokenUsage(tokenUsage);
            }

            var chatResponse = ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(metadataBuilder.build())
                    .build();

            try {
                handler.onCompleteResponse(chatResponse);
            } catch (Exception e) {
                logger.debug("Exception from onCompleteResponse, calling onError", e);
                safeOnError(handler, e);
            }
        }
    }
}
