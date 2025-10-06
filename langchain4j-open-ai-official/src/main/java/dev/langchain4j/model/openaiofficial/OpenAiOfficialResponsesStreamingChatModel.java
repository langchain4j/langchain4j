package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseErrorEvent;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseFunctionCallArgumentsDeltaEvent;
import com.openai.models.responses.ResponseFunctionCallArgumentsDoneEvent;
import com.openai.models.responses.ResponseInProgressEvent;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseIncompleteEvent;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItemAddedEvent;
import com.openai.models.responses.ResponseOutputItemDoneEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
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
    private final String reasoningEffort;
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
        this.reasoningEffort = builder.reasoningEffort;
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
                inputItems.add(toResponseInputItem(msg));
            }
            paramsBuilder.inputOfResponse(inputItems);
            logger.debug("Converted {} messages to input items", inputItems.size());

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
                paramsBuilder.maxOutputTokens(Math.max(effectiveMaxOutputTokens, 16));
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
            if (reasoningEffort != null && !reasoningEffort.isEmpty()) {
                paramsBuilder.reasoning(Reasoning.builder()
                        .effort(ReasoningEffort.of(reasoningEffort))
                        .build());
            }

            // Add tools if present
            if (chatRequest.toolSpecifications() != null
                    && !chatRequest.toolSpecifications().isEmpty()) {
                for (var toolSpec : chatRequest.toolSpecifications()) {
                    paramsBuilder.addTool(toResponsesTool(toolSpec, strict));
                }
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
                    handler.onError(e);
                } catch (Exception e) {
                    logger.error("Exception in stream processing: {}", e.getMessage(), e);
                    handler.onError(e);
                } finally {
                    if (finalCancellationMonitorFuture != null) {
                        finalCancellationMonitorFuture.cancel(false);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Exception in doChat: {}", e.getMessage(), e);
            handler.onError(e);
            if (cancellationMonitorFuture != null) {
                cancellationMonitorFuture.cancel(false);
            }
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.builder().modelName(modelName).build();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return Collections.unmodifiableList(listeners);
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    private static ResponseInputItem toResponseInputItem(ChatMessage msg) {
        if (msg instanceof SystemMessage systemMessage) {
            return createTextMessage(EasyInputMessage.Role.SYSTEM, systemMessage.text());
        } else if (msg instanceof UserMessage userMessage) {
            var text = userMessage.contents().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .reduce("", (a, b) -> a + b);
            return createTextMessage(EasyInputMessage.Role.USER, text);
        } else if (msg instanceof AiMessage aiMessage) {
            var text = aiMessage.text();
            return createTextMessage(EasyInputMessage.Role.ASSISTANT, text != null ? text : "");
        } else {
            return createTextMessage(EasyInputMessage.Role.USER, msg.toString());
        }
    }

    private static ResponseInputItem createTextMessage(EasyInputMessage.Role role, String text) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(EasyInputMessage.Content.ofTextInput(text))
                .build());
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
        private String reasoningEffort;
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

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
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
        private final List<ToolExecutionRequest> completedToolCalls = new ArrayList<>();
        private final StringBuilder textBuilder = new StringBuilder();
        private OpenAiOfficialTokenUsage tokenUsage;
        private String responseId;
        private String finishReason;

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
                } else if (event.isOutputTextDelta()) {
                    handleOutputTextDelta(event.asOutputTextDelta());
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
                    logger.debug("Unhandled event type: {}", event.getClass().getSimpleName());
                }
            } catch (Exception e) {
                logger.error("Error handling event: {}", e.getMessage(), e);
                handler.onError(e);
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

        private void handleOutputTextDelta(ResponseTextDeltaEvent event) {
            var delta = event.delta();
            if (!delta.isEmpty()) {
                textBuilder.append(delta);
                handler.onPartialResponse(delta);
            }
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
                } else {
                    logger.warn("Function call missing item ID: {}", functionCall.callId());
                }
            }
        }

        private void handleFunctionCallArgumentsDelta(ResponseFunctionCallArgumentsDeltaEvent event) {
            // Delta events are ignored
        }

        private void handleFunctionCallArgumentsDone(ResponseFunctionCallArgumentsDoneEvent event) {
            var builder = toolCallBuilders.remove(event.itemId());
            if (builder != null) {
                builder.arguments(event.arguments());
                completedToolCalls.add(builder.build());
            } else {
                logger.warn("No builder for itemId in argumentsDone: {}", event.itemId());
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

            handler.onCompleteResponse(chatResponse);
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
            handler.onError(new RuntimeException("Response error: " + message));
        }

        private void handleFailed(ResponseFailedEvent event) {
            var response = event.response();
            var error = response.error();
            logger.error("Response failed: {}", error);
            handler.onError(new RuntimeException("Response failed: " + error));
        }

        private void handleIncomplete(ResponseIncompleteEvent event) {
            var response = event.response();
            var incompleteDetails = response.incompleteDetails();
            logger.error("Response incomplete: {}", incompleteDetails);
            handler.onError(new RuntimeException("Response incomplete: " + incompleteDetails));
        }
    }
}
