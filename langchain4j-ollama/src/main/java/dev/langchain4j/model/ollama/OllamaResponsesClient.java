package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.CancellationUnsupportedHandle;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

class OllamaResponsesClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private static final String DEFAULT_API_KEY = "ollama";

    // Event types
    private static final String EVENT_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    private static final String EVENT_OUTPUT_ITEM_ADDED = "response.output_item.added";
    private static final String EVENT_FUNCTION_CALL_ARGUMENTS_DELTA = "response.function_call_arguments.delta";
    private static final String EVENT_FUNCTION_CALL_ARGUMENTS_DONE = "response.function_call_arguments.done";
    private static final String EVENT_OUTPUT_ITEM_DONE = "response.output_item.done";
    private static final String EVENT_REASONING_TEXT_DELTA = "response.reasoning_text.delta";
    private static final String EVENT_REASONING_SUMMARY_TEXT_DELTA = "response.reasoning_summary_text.delta";
    private static final String EVENT_RESPONSE_COMPLETED = "response.completed";
    private static final String EVENT_RESPONSE_INCOMPLETE = "response.incomplete";
    private static final String EVENT_RESPONSE_FAILED = "response.failed";
    private static final String EVENT_RESPONSE_ERROR = "response.error";

    // Field names
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_PARAMETERS = "parameters";
    private static final String FIELD_PROPERTIES = "properties";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_DELTA = "delta";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_IMAGE_URL = "image_url";
    private static final String FIELD_DETAIL = "detail";
    private static final String FIELD_ITEM = "item";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CALL_ID = "call_id";
    private static final String FIELD_ITEM_ID = "item_id";
    private static final String FIELD_OUTPUT_INDEX = "output_index";
    private static final String FIELD_RESPONSE = "response";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_OUTPUT = "output";
    private static final String FIELD_USAGE = "usage";
    private static final String FIELD_INPUT_TOKENS = "input_tokens";
    private static final String FIELD_OUTPUT_TOKENS = "output_tokens";
    private static final String FIELD_TOTAL_TOKENS = "total_tokens";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_INPUT = "input";
    private static final String FIELD_STREAM = "stream";
    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_TOP_P = "top_p";
    private static final String FIELD_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String FIELD_INSTRUCTIONS = "instructions";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_TOOL_CHOICE = "tool_choice";
    private static final String FIELD_STRICT = "strict";
    private static final String FIELD_TEXT_VERBOSITY = "verbosity";
    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_SCHEMA = "schema";
    private static final String FIELD_ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_SUMMARY = "summary";
    private static final String FIELD_SUMMARY_TEXT = "summary_text";
    private static final String DEFAULT_IMAGE_MIME_TYPE = "image/jpeg";

    // Roles
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    // Types
    private static final String TYPE_FUNCTION = "function";
    private static final String TYPE_FUNCTION_CALL = "function_call";
    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_REASONING = "reasoning";
    private static final String TYPE_OUTPUT_TEXT = "output_text";
    private static final String TYPE_INPUT_TEXT = "input_text";
    private static final String TYPE_INPUT_IMAGE = "input_image";
    private static final String TYPE_FUNCTION_CALL_OUTPUT = "function_call_output";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_JSON_SCHEMA = "json_schema";
    private static final String TYPE_OBJECT = "object";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final Supplier<Map<String, String>> customHeadersSupplier;

    OllamaResponsesClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(builder.timeout, Duration.ofSeconds(15)))
                .readTimeout(getOrDefault(builder.timeout, Duration.ofSeconds(60)))
                .build();
        if (builder.logRequests || builder.logResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }
        this.baseUrl = builder.baseUrl;
        this.apiKey = getOrDefault(builder.apiKey, DEFAULT_API_KEY);
        this.customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, () -> Map::of);
    }

    static Builder builder() {
        return new Builder();
    }

    ChatResponse chat(ChatRequest chatRequest, OllamaResponsesChatRequestParameters parameters) {
        try {
            Map<String, Object> payload = buildRequestPayload(chatRequest, parameters, false);
            HttpRequest request = buildHttpRequest(payload, false);
            SuccessfulHttpResponse rawHttpResponse = httpClient.execute(request);
            return parseChatResponse(rawHttpResponse);
        } catch (Exception e) {
            throw ExceptionMapper.DEFAULT.mapException(e);
        }
    }

    void streamingChat(
            ChatRequest chatRequest,
            OllamaResponsesChatRequestParameters parameters,
            StreamingChatResponseHandler handler) {
        try {
            Map<String, Object> payload = buildRequestPayload(chatRequest, parameters, true);
            HttpRequest request = buildHttpRequest(payload, true);
            httpClient.execute(request, new DefaultServerSentEventParser(), new ResponsesApiEventListener(handler));
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(ExceptionMapper.DEFAULT.mapException(e)));
        }
    }

    private Map<String, Object> buildRequestPayload(
            ChatRequest chatRequest, OllamaResponsesChatRequestParameters parameters, boolean stream) {
        List<Map<String, Object>> input = new ArrayList<>();
        for (ChatMessage message : chatRequest.messages()) {
            input.addAll(toResponsesMessages(message));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(FIELD_MODEL, parameters.modelName());
        payload.put(FIELD_INPUT, input);
        payload.put(FIELD_STREAM, stream);

        if (parameters.temperature() != null) {
            payload.put(FIELD_TEMPERATURE, parameters.temperature());
        }
        if (parameters.topP() != null) {
            payload.put(FIELD_TOP_P, parameters.topP());
        }
        if (parameters.maxOutputTokens() != null) {
            payload.put(FIELD_MAX_OUTPUT_TOKENS, parameters.maxOutputTokens());
        }
        if (parameters.instructions() != null && !parameters.instructions().isEmpty()) {
            payload.put(FIELD_INSTRUCTIONS, parameters.instructions());
        }

        List<Map<String, Object>> tools = new ArrayList<>();
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            for (ToolSpecification toolSpec : toolSpecifications) {
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put(FIELD_TYPE, TYPE_FUNCTION);
                tool.put(FIELD_NAME, toolSpec.name());
                if (toolSpec.description() != null) {
                    tool.put(FIELD_DESCRIPTION, toolSpec.description());
                }
                if (toolSpec.parameters() != null) {
                    tool.put(FIELD_PARAMETERS, toMap(toolSpec.parameters(), false));
                }
                tools.add(tool);
            }
        }
        if (!tools.isEmpty()) {
            payload.put(FIELD_TOOLS, tools);
            if (parameters.toolChoice() != null) {
                payload.put(FIELD_TOOL_CHOICE, toToolChoiceString(parameters.toolChoice()));
            }
        }

        Map<String, Object> textConfig = toResponseTextConfig(parameters.responseFormat());
        if (textConfig != null) {
            payload.put("text", textConfig);
        }

        return payload;
    }

    private HttpRequest buildHttpRequest(Map<String, Object> payload, boolean stream) throws Exception {
        String requestBody = OBJECT_MAPPER.writeValueAsString(payload);

        HttpRequest.Builder requestBuilder = HttpRequest.builder()
                .url(baseUrl + "/v1/responses")
                .method(HttpMethod.POST)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", stream ? "text/event-stream" : "application/json")
                .addHeader("Authorization", "Bearer " + apiKey);

        Map<String, String> dynamicHeaders = customHeadersSupplier.get();
        if (!isNullOrEmpty(dynamicHeaders)) {
            requestBuilder.addHeaders(dynamicHeaders);
        }

        return requestBuilder.body(requestBody).build();
    }

    private ChatResponse parseChatResponse(SuccessfulHttpResponse rawHttpResponse) throws Exception {
        JsonNode responseNode = OBJECT_MAPPER.readTree(rawHttpResponse.body());

        JsonNode outputNode = responseNode.path(FIELD_OUTPUT);
        String text = extractText(outputNode);
        String thinking = extractReasoningSummary(outputNode);
        List<ToolExecutionRequest> toolExecutionRequests = extractToolExecutionRequests(outputNode);

        AiMessage aiMessage = AiMessage.builder()
                .text(text)
                .thinking(thinking)
                .toolExecutionRequests(toolExecutionRequests)
                .build();

        TokenUsage tokenUsage = parseTokenUsage(responseNode.path(FIELD_USAGE));
        FinishReason finishReason =
                finishReasonFromStatus(responseNode.path(FIELD_STATUS).asText(null), !toolExecutionRequests.isEmpty());

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(dev.langchain4j.model.chat.response.ChatResponseMetadata.builder()
                        .id(responseNode.path(FIELD_ID).asText(null))
                        .modelName(responseNode.path(FIELD_MODEL).asText(null))
                        .tokenUsage(tokenUsage)
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private static String extractText(JsonNode output) {
        if (!output.isArray()) {
            return null;
        }
        StringBuilder textBuilder = new StringBuilder();
        for (JsonNode item : output) {
            if (TYPE_MESSAGE.equals(item.path(FIELD_TYPE).asText())) {
                JsonNode content = item.path(FIELD_CONTENT);
                if (content.isArray()) {
                    for (JsonNode c : content) {
                        if (TYPE_OUTPUT_TEXT.equals(c.path(FIELD_TYPE).asText())) {
                            textBuilder.append(c.path(FIELD_TEXT).asText());
                        }
                    }
                }
            }
        }
        return textBuilder.isEmpty() ? null : textBuilder.toString();
    }

    private static String extractReasoningSummary(JsonNode output) {
        if (!output.isArray()) {
            return null;
        }
        StringBuilder summaryBuilder = new StringBuilder();
        for (JsonNode item : output) {
            if (TYPE_REASONING.equals(item.path(FIELD_TYPE).asText())) {
                JsonNode summaryArray = item.path(FIELD_SUMMARY);
                if (summaryArray.isArray()) {
                    for (JsonNode summaryItem : summaryArray) {
                        if (FIELD_SUMMARY_TEXT.equals(summaryItem.path(FIELD_TYPE).asText())) {
                            summaryBuilder.append(summaryItem.path(FIELD_TEXT).asText());
                        }
                    }
                }
            }
        }
        return summaryBuilder.isEmpty() ? null : summaryBuilder.toString();
    }

    private static List<ToolExecutionRequest> extractToolExecutionRequests(JsonNode output) {
        if (!output.isArray()) {
            return List.of();
        }
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        for (JsonNode item : output) {
            if (!TYPE_FUNCTION_CALL.equals(item.path(FIELD_TYPE).asText())) {
                continue;
            }
            String id = item.path(FIELD_CALL_ID).asText(null);
            if (id == null || id.isBlank()) {
                id = item.path(FIELD_ID).asText(null);
            }
            toolExecutionRequests.add(ToolExecutionRequest.builder()
                    .id(id)
                    .name(item.path(FIELD_NAME).asText())
                    .arguments(item.path(FIELD_ARGUMENTS).asText("{}"))
                    .build());
        }
        return toolExecutionRequests;
    }

    private static TokenUsage parseTokenUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            return null;
        }
        return new TokenUsage(
                usageNode.path(FIELD_INPUT_TOKENS).asInt(),
                usageNode.path(FIELD_OUTPUT_TOKENS).asInt());
    }

    private static FinishReason finishReasonFromStatus(String status, boolean hasToolCalls) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case "completed" -> hasToolCalls ? FinishReason.TOOL_EXECUTION : FinishReason.STOP;
            case "incomplete" -> FinishReason.LENGTH;
            case "failed" -> FinishReason.OTHER;
            default -> FinishReason.OTHER;
        };
    }

    private static List<Map<String, Object>> toResponsesMessages(ChatMessage msg) {
        if (msg instanceof SystemMessage systemMessage) {
            return List.of(createMessageEntry(ROLE_SYSTEM, List.of(createInputTextContent(systemMessage.text()))));
        } else if (msg instanceof UserMessage userMessage) {
            List<Map<String, Object>> contentEntries = new ArrayList<>();
            for (Content content : userMessage.contents()) {
                if (content instanceof TextContent textContent) {
                    contentEntries.add(createInputTextContent(textContent.text()));
                } else if (content instanceof ImageContent imageContent) {
                    contentEntries.add(createInputImageContent(imageContent.image(), imageContent.detailLevel()));
                } else {
                    throw new UnsupportedFeatureException(
                            "Unsupported content type: " + content.getClass().getName());
                }
            }
            return List.of(createMessageEntry(ROLE_USER, contentEntries));
        } else if (msg instanceof AiMessage aiMessage) {
            List<Map<String, Object>> items = new ArrayList<>();
            var text = aiMessage.text();
            if (text != null && !text.isEmpty()) {
                items.add(createMessageEntry(ROLE_ASSISTANT, List.of(createOutputTextContent(text))));
            }
            if (aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    var functionCall = new LinkedHashMap<String, Object>();
                    functionCall.put(FIELD_TYPE, TYPE_FUNCTION_CALL);
                    functionCall.put(FIELD_CALL_ID, toolRequest.id());
                    functionCall.put(FIELD_NAME, toolRequest.name());
                    functionCall.put(FIELD_ARGUMENTS, toolRequest.arguments());
                    items.add(functionCall);
                }
            }
            return items;
        } else if (msg instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            var outputEntry = new LinkedHashMap<String, Object>();
            outputEntry.put(FIELD_TYPE, TYPE_FUNCTION_CALL_OUTPUT);
            outputEntry.put(FIELD_CALL_ID, toolExecutionResultMessage.id());
            outputEntry.put(FIELD_OUTPUT, toolExecutionResultMessage.text());
            return List.of(outputEntry);
        } else {
            throw new UnsupportedFeatureException(
                    "Unsupported message type: " + msg.getClass().getName());
        }
    }

    private static Map<String, Object> createMessageEntry(String role, List<Map<String, Object>> contentEntries) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put(FIELD_TYPE, TYPE_MESSAGE);
        entry.put(FIELD_ROLE, role);
        entry.put(FIELD_CONTENT, contentEntries);
        return entry;
    }

    private static Map<String, Object> createInputTextContent(String text) {
        var content = new LinkedHashMap<String, Object>();
        content.put(FIELD_TYPE, TYPE_INPUT_TEXT);
        content.put(FIELD_TEXT, text);
        return content;
    }

    private static Map<String, Object> createOutputTextContent(String text) {
        var content = new LinkedHashMap<String, Object>();
        content.put(FIELD_TYPE, TYPE_OUTPUT_TEXT);
        content.put(FIELD_TEXT, text);
        return content;
    }

    private static Map<String, Object> createInputImageContent(Image image, ImageContent.DetailLevel detailLevel) {
        var content = new LinkedHashMap<String, Object>();
        content.put(FIELD_TYPE, TYPE_INPUT_IMAGE);
        if (image.url() != null) {
            content.put(FIELD_IMAGE_URL, image.url().toString());
        } else if (image.base64Data() != null) {
            String mimeType = image.mimeType() != null ? image.mimeType() : DEFAULT_IMAGE_MIME_TYPE;
            content.put(FIELD_IMAGE_URL, "data:" + mimeType + ";base64," + image.base64Data());
        }
        content.put(FIELD_DETAIL, toDetailString(detailLevel));
        return content;
    }

    private static String toDetailString(ImageContent.DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> "low";
            case HIGH -> "high";
            case AUTO -> "auto";
            default -> "auto";
        };
    }

    private static String toToolChoiceString(ToolChoice toolChoice) {
        if (toolChoice == null) return null;
        return switch (toolChoice) {
            case AUTO -> "auto";
            case REQUIRED -> "required";
            case NONE -> "none";
        };
    }

    private static Map<String, Object> toResponseTextConfig(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == ResponseFormatType.TEXT) {
            return null;
        }
        var textConfig = new LinkedHashMap<String, Object>();
        JsonSchema jsonSchema = responseFormat.jsonSchema();
        if (jsonSchema == null) {
            var format = new LinkedHashMap<String, Object>();
            format.put(FIELD_TYPE, TYPE_JSON_OBJECT);
            textConfig.put(FIELD_FORMAT, format);
        } else {
            if (!(jsonSchema.rootElement() instanceof JsonObjectSchema
                    || jsonSchema.rootElement() instanceof JsonRawSchema)) {
                throw new IllegalArgumentException(
                        "Root element must be JsonObjectSchema or JsonRawSchema, but was: "
                                + jsonSchema.rootElement().getClass());
            }
            var format = new LinkedHashMap<String, Object>();
            format.put(FIELD_TYPE, TYPE_JSON_SCHEMA);
            if (jsonSchema.name() != null) {
                format.put(FIELD_NAME, jsonSchema.name());
            }
            format.put(FIELD_SCHEMA, toMap(jsonSchema.rootElement(), false));
            textConfig.put(FIELD_FORMAT, format);
        }
        return textConfig;
    }

    static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;
        private Supplier<Map<String, String>> customHeadersSupplier;

        Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            if (logRequests != null) this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            if (logResponses != null) this.logResponses = logResponses;
            return this;
        }

        Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        OllamaResponsesClient build() {
            return new OllamaResponsesClient(this);
        }
    }

    private static class ResponsesApiEventListener implements ServerSentEventListener {

        private final StreamingChatResponseHandler handler;
        private volatile StreamingHandle streamingHandle;
        private final Map<String, ToolExecutionRequest.Builder> toolCallBuilders = new LinkedHashMap<>();
        private final Map<String, Integer> toolCallIndices = new LinkedHashMap<>();
        private final List<ToolExecutionRequest> completedToolCalls = new ArrayList<>();
        private final Set<String> completedToolCallItemIds = new HashSet<>();

        ResponsesApiEventListener(StreamingChatResponseHandler handler) {
            this.handler = handler;
        }

        private boolean isCancelled() {
            return streamingHandle != null && streamingHandle.isCancelled();
        }

        @Override
        public void onEvent(ServerSentEvent event) {
            onEvent(event, new ServerSentEventContext(new CancellationUnsupportedHandle()));
        }

        @Override
        public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
            if (streamingHandle == null) {
                streamingHandle = toStreamingHandle(context.parsingHandle());
            }
            if (isCancelled()) return;

            var data = event.data();
            if (data == null || data.isEmpty()) return;

            handleDelta(data);
        }

        @Override
        public void onError(Throwable error) {
            withLoggingExceptions(() -> handler.onError(ExceptionMapper.DEFAULT.mapException(error)));
        }

        private void handleDelta(String data) {
            if (!data.trim().startsWith("{") && !data.trim().startsWith("[")) return;

            try {
                var node = OBJECT_MAPPER.readTree(data);
                var type = node.has(FIELD_TYPE) ? node.get(FIELD_TYPE).asText() : "";

                if (EVENT_OUTPUT_TEXT_DELTA.equals(type)) {
                    var text = node.path(FIELD_DELTA).asText();
                    if (!text.isEmpty()) {
                        onPartialResponse(handler, text, streamingHandle);
                    }
                } else if (EVENT_REASONING_TEXT_DELTA.equals(type)
                        || EVENT_REASONING_SUMMARY_TEXT_DELTA.equals(type)) {
                    var thinking = node.path(FIELD_DELTA).asText();
                    if (!thinking.isEmpty()) {
                        onPartialThinking(handler, thinking, streamingHandle);
                    }
                } else if (EVENT_OUTPUT_ITEM_ADDED.equals(type)) {
                    var item = node.path(FIELD_ITEM);
                    if (TYPE_FUNCTION_CALL.equals(item.path(FIELD_TYPE).asText())) {
                        var itemId = item.path(FIELD_ID).asText();
                        int outputIndex = node.path(FIELD_OUTPUT_INDEX).asInt(0);
                        toolCallBuilders.put(
                                itemId,
                                ToolExecutionRequest.builder()
                                        .id(item.path(FIELD_CALL_ID).asText())
                                        .name(item.path(FIELD_NAME).asText())
                                        .arguments(""));
                        toolCallIndices.putIfAbsent(itemId, outputIndex);
                    }
                } else if (EVENT_FUNCTION_CALL_ARGUMENTS_DELTA.equals(type)) {
                    var itemId = node.path(FIELD_ITEM_ID).asText();
                    var builder = toolCallBuilders.get(itemId);
                    if (builder != null) {
                        var currentArgs = builder.build().arguments();
                        String delta = node.path(FIELD_DELTA).asText();
                        builder.arguments(currentArgs + delta);
                        Integer index = toolCallIndices.get(itemId);
                        if (index != null && !delta.isEmpty()) {
                            PartialToolCall partialToolCall = PartialToolCall.builder()
                                    .index(index)
                                    .id(builder.build().id())
                                    .name(builder.build().name())
                                    .partialArguments(delta)
                                    .build();
                            InternalStreamingChatResponseHandlerUtils.onPartialToolCall(
                                    handler, partialToolCall, streamingHandle);
                        }
                    }
                } else if (EVENT_FUNCTION_CALL_ARGUMENTS_DONE.equals(type)) {
                    var itemId = node.path(FIELD_ITEM_ID).asText();
                    var builder = toolCallBuilders.get(itemId);
                    if (builder != null) {
                        builder.arguments(node.path(FIELD_ARGUMENTS).asText());
                        completeToolCall(itemId, builder);
                    }
                } else if (EVENT_OUTPUT_ITEM_DONE.equals(type)) {
                    handleOutputItemDone(node);
                } else if (EVENT_RESPONSE_COMPLETED.equals(type)
                        || EVENT_RESPONSE_INCOMPLETE.equals(type)) {
                    handleResponseCompleted(node);
                } else if (EVENT_RESPONSE_FAILED.equals(type)
                        || EVENT_RESPONSE_ERROR.equals(type)) {
                    handleResponseFailure(node);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void handleOutputItemDone(JsonNode node) {
            var item = node.path(FIELD_ITEM);
            if (TYPE_FUNCTION_CALL.equals(item.path(FIELD_TYPE).asText())) {
                var itemId = item.path(FIELD_ID).asText();
                int outputIndex = node.path(FIELD_OUTPUT_INDEX).asInt(0);
                var builder =
                        toolCallBuilders.computeIfAbsent(itemId, ignored -> ToolExecutionRequest.builder());
                toolCallIndices.putIfAbsent(itemId, outputIndex);

                var callIdNode = item.get(FIELD_CALL_ID);
                if (callIdNode != null && !callIdNode.isNull()) {
                    builder.id(callIdNode.asText());
                }
                var nameNode = item.get(FIELD_NAME);
                if (nameNode != null && !nameNode.isNull()) {
                    builder.name(nameNode.asText());
                }
                var argumentsNode = item.get(FIELD_ARGUMENTS);
                if (argumentsNode != null && !argumentsNode.isNull()) {
                    builder.arguments(argumentsNode.asText());
                }
                completeToolCall(itemId, builder);
            }
        }

        private void handleResponseCompleted(JsonNode node) {
            var responseNode = node.path(FIELD_RESPONSE);
            JsonNode outputNode = responseNode.path(FIELD_OUTPUT);
            String text = extractText(outputNode);
            String thinking = extractReasoningSummary(outputNode);

            AiMessage aiMessage = AiMessage.builder()
                    .text(text)
                    .thinking(thinking)
                    .toolExecutionRequests(completedToolCalls)
                    .build();

            TokenUsage tokenUsage = parseTokenUsage(responseNode.path(FIELD_USAGE));
            FinishReason finishReason = finishReasonFromStatus(
                    responseNode.path(FIELD_STATUS).asText(null), !completedToolCalls.isEmpty());

            var response = ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(dev.langchain4j.model.chat.response.ChatResponseMetadata.builder()
                            .id(responseNode.path(FIELD_ID).asText(null))
                            .modelName(responseNode.path(FIELD_MODEL).asText(null))
                            .tokenUsage(tokenUsage)
                            .finishReason(finishReason)
                            .build())
                    .build();

            if (!isCancelled()) {
                try {
                    onCompleteResponse(handler, response);
                } catch (Exception e) {
                    withLoggingExceptions(() -> handler.onError(e));
                }
            }
        }

        private void handleResponseFailure(JsonNode node) {
            JsonNode errorNode = node.path(FIELD_ERROR);
            if (errorNode.isMissingNode()) {
                errorNode = node.path(FIELD_RESPONSE).path(FIELD_ERROR);
            }
            String message = buildErrorMessage(errorNode);
            withLoggingExceptions(() -> handler.onError(new RuntimeException(message)));
        }

        private static String buildErrorMessage(JsonNode errorNode) {
            if (errorNode != null && !errorNode.isMissingNode() && !errorNode.isNull()) {
                String msg = errorNode.path(FIELD_MESSAGE).asText(null);
                if (msg != null && !msg.isBlank()) {
                    return "Response failed: " + msg;
                }
            }
            return "Response failed";
        }

        private void completeToolCall(String itemId, ToolExecutionRequest.Builder builder) {
            if (builder == null || completedToolCallItemIds.contains(itemId)) return;
            ToolExecutionRequest toolExecutionRequest = builder.build();
            completedToolCalls.add(toolExecutionRequest);
            completedToolCallItemIds.add(itemId);
            toolCallBuilders.remove(itemId);
            Integer index = toolCallIndices.remove(itemId);
            int safeIndex = index != null ? index : completedToolCalls.size() - 1;
            if (!isCancelled()) {
                onCompleteToolCall(handler, new CompleteToolCall(safeIndex, toolExecutionRequest));
            }
        }
    }
}
