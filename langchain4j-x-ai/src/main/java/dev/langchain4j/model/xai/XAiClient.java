package dev.langchain4j.model.xai;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.ToolSpecificationUtils.isEffectivelyStrict;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

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
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.reactive.streaming.HttpStreamingChatPublisher;
import dev.langchain4j.reactive.streaming.ReactiveStreamingDefaults;
import dev.langchain4j.reactive.streaming.TubeBackedStreamingChatResponseHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Supplier;
import mutiny.zero.Tube;

/**
 * Talks to the <a href="https://docs.x.ai/developers/model-capabilities/text/generate-text">xAI Responses API</a>.
 */
class XAiClient {

    static final Json.JsonCodec CODEC =
            ProviderJson.codec(ProviderJsonSpec.builder().build());

    static final String DEFAULT_BASE_URL = "https://api.x.ai/v1";

    static final String ENCRYPTED_REASONING_KEY = "encrypted_reasoning";

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_PARAMETERS = "parameters";
    private static final String FIELD_PROPERTIES = "properties";
    private static final String FIELD_REQUIRED = "required";
    private static final String FIELD_ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_IMAGE_URL = "image_url";
    private static final String FIELD_DETAIL = "detail";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CALL_ID = "call_id";
    private static final String FIELD_OUTPUT = "output";
    private static final String FIELD_USAGE = "usage";
    private static final String FIELD_INPUT_TOKENS = "input_tokens";
    private static final String FIELD_OUTPUT_TOKENS = "output_tokens";
    private static final String FIELD_TOTAL_TOKENS = "total_tokens";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_INPUT = "input";
    private static final String FIELD_STREAM = "stream";
    private static final String FIELD_STORE = "store";
    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_TOP_P = "top_p";
    private static final String FIELD_TOP_K = "top_k";
    private static final String FIELD_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String FIELD_MAX_TOOL_CALLS = "max_tool_calls";
    private static final String FIELD_PARALLEL_TOOL_CALLS = "parallel_tool_calls";
    private static final String FIELD_PREVIOUS_RESPONSE_ID = "previous_response_id";
    private static final String FIELD_INCLUDE = "include";
    private static final String FIELD_PROMPT_CACHE_KEY = "prompt_cache_key";
    private static final String FIELD_USER = "user";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_TOOL_CHOICE = "tool_choice";
    private static final String FIELD_REASONING = "reasoning";
    private static final String FIELD_EFFORT = "effort";
    private static final String FIELD_SUMMARY = "summary";
    private static final String FIELD_ENCRYPTED_CONTENT = "encrypted_content";
    private static final String FIELD_STRICT = "strict";
    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_SCHEMA = "schema";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_INCOMPLETE_DETAILS = "incomplete_details";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_CREATED_AT = "created_at";

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_REASONING = "reasoning";
    private static final String TYPE_SUMMARY_TEXT = "summary_text";
    private static final String TYPE_OUTPUT_TEXT = "output_text";
    private static final String TYPE_INPUT_TEXT = "input_text";
    private static final String TYPE_INPUT_IMAGE = "input_image";
    private static final String TYPE_FUNCTION = "function";
    private static final String TYPE_FUNCTION_CALL = "function_call";
    private static final String TYPE_FUNCTION_CALL_OUTPUT = "function_call_output";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_JSON_SCHEMA = "json_schema";

    private static final String DEFAULT_IMAGE_MIME_TYPE = "image/jpeg";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final int streamingBufferSize;
    private final Supplier<Map<String, String>> customHeadersSupplier;

    private XAiClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(builder.connectTimeout, httpClientBuilder.connectTimeout()))
                .readTimeout(getOrDefault(builder.readTimeout, httpClientBuilder.readTimeout()))
                .build();
        if (builder.logRequests || builder.logResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }
        this.baseUrl = getOrDefault(builder.baseUrl, DEFAULT_BASE_URL);
        this.apiKey = builder.apiKey;
        this.streamingBufferSize = ensureGreaterThanZero(
                getOrDefault(builder.streamingBufferSize, ReactiveStreamingDefaults.DEFAULT_BUFFER_SIZE),
                "streamingBufferSize");
        this.customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, () -> Map::of);
    }

    static Builder builder() {
        return new Builder();
    }

    ChatResponse chat(ChatRequest chatRequest, XAiChatRequestParameters parameters) throws Exception {
        HttpRequest request = buildHttpRequest(buildRequestPayload(chatRequest, parameters, false), false);
        return parseChatResponse(httpClient.execute(request));
    }

    CompletableFuture<ChatResponse> chatAsync(ChatRequest chatRequest, XAiChatRequestParameters parameters) {
        CompletableFuture<SuccessfulHttpResponse> httpFuture;
        try {
            httpFuture = httpClient.executeAsync(
                    buildHttpRequest(buildRequestPayload(chatRequest, parameters, false), false));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(ExceptionMapper.DEFAULT.mapException(e));
        }

        CompletableFuture<ChatResponse> result = httpFuture.thenApply(rawHttpResponse -> {
            try {
                return parseChatResponse(rawHttpResponse);
            } catch (Exception e) {
                throw ExceptionMapper.DEFAULT.mapException(e);
            }
        });
        propagateCancellation(result, httpFuture);
        return result;
    }

    void streamingChat(
            ChatRequest chatRequest, XAiChatRequestParameters parameters, StreamingChatResponseHandler handler) {
        try {
            HttpRequest request = buildHttpRequest(buildRequestPayload(chatRequest, parameters, true), true);
            httpClient.execute(request, new DefaultServerSentEventParser(), new XAiStreamingEventListener(handler));
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(ExceptionMapper.DEFAULT.mapException(e)));
        }
    }

    Publisher<ChatModelStreamingEvent> streamingChatPublisher(
            ChatRequest chatRequest, XAiChatRequestParameters parameters) {
        return HttpStreamingChatPublisher.create(
                streamingBufferSize,
                () -> {
                    try {
                        return httpClient.stream(
                                buildHttpRequest(buildRequestPayload(chatRequest, parameters, true), true));
                    } catch (Exception e) {
                        throw ExceptionMapper.DEFAULT.mapException(e);
                    }
                },
                EventSink::new);
    }

    /**
     * Feeds the events of one {@link Publisher} subscription into a {@link XAiStreamingEventListener}.
     */
    private static final class EventSink implements HttpStreamingChatPublisher.Sink {

        private final Tube<ChatModelStreamingEvent> tube;
        private final XAiStreamingEventListener listener;

        EventSink(Tube<ChatModelStreamingEvent> tube) {
            this.tube = tube;
            this.listener = new XAiStreamingEventListener(new TubeBackedStreamingChatResponseHandler(tube));
        }

        @Override
        public void onEvent(HttpStreamingEvent item) {
            if (tube.cancelled()) {
                return;
            }
            try {
                if (item instanceof HttpResponseReceived responseReceived) {
                    listener.onOpen(responseReceived.response());
                } else if (item instanceof ServerSentEvent event) {
                    listener.onEvent(event);
                }
            } catch (Exception e) {
                if (!tube.cancelled()) {
                    tube.fail(e);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!tube.cancelled()) {
                listener.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!tube.cancelled()) {
                tube.complete();
            }
        }
    }

    Map<String, Object> buildRequestPayload(
            ChatRequest chatRequest, XAiChatRequestParameters parameters, boolean stream) {
        List<Map<String, Object>> input = new ArrayList<>();
        for (ChatMessage message : chatRequest.messages()) {
            input.addAll(toInputItems(message));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(FIELD_MODEL, parameters.modelName());
        payload.put(FIELD_INPUT, input);
        if (stream) {
            payload.put(FIELD_STREAM, true);
        }
        putIfNotNull(payload, FIELD_STORE, parameters.store());
        putIfNotNull(payload, FIELD_TEMPERATURE, parameters.temperature());
        putIfNotNull(payload, FIELD_TOP_P, parameters.topP());
        putIfNotNull(payload, FIELD_TOP_K, parameters.topK());
        putIfNotNull(payload, FIELD_MAX_OUTPUT_TOKENS, parameters.maxOutputTokens());
        putIfNotNull(payload, FIELD_MAX_TOOL_CALLS, parameters.maxToolCalls());
        putIfNotNull(payload, FIELD_PARALLEL_TOOL_CALLS, parameters.parallelToolCalls());
        putIfNotNull(payload, FIELD_PREVIOUS_RESPONSE_ID, parameters.previousResponseId());
        putIfNotNull(payload, FIELD_PROMPT_CACHE_KEY, parameters.promptCacheKey());
        putIfNotNull(payload, FIELD_USER, parameters.user());

        if (parameters.include() != null && !parameters.include().isEmpty()) {
            payload.put(FIELD_INCLUDE, parameters.include());
        }

        if (parameters.reasoningEffort() != null) {
            payload.put(FIELD_REASONING, Map.of(FIELD_EFFORT, parameters.reasoningEffort()));
        }

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolSpecification toolSpecification : toolSpecifications) {
                tools.add(toTool(toolSpecification));
            }
            payload.put(FIELD_TOOLS, tools);
            if (parameters.toolChoice() != null) {
                payload.put(FIELD_TOOL_CHOICE, toToolChoiceString(parameters.toolChoice()));
            }
        }

        Map<String, Object> textConfig = toTextConfig(parameters.responseFormat());
        if (textConfig != null) {
            payload.put(FIELD_TEXT, textConfig);
        }

        return payload;
    }

    private HttpRequest buildHttpRequest(Map<String, Object> payload, boolean stream) {
        HttpRequest.Builder requestBuilder = HttpRequest.builder()
                .url(baseUrl + "/responses")
                .method(HttpMethod.POST)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", stream ? "text/event-stream" : "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        requestBuilder.addHeaders(customHeadersSupplier.get());

        return requestBuilder.body(CODEC.toJson(payload)).build();
    }

    private static void putIfNotNull(Map<String, Object> payload, String field, Object value) {
        if (value != null) {
            payload.put(field, value);
        }
    }

    // --- request mapping ---

    private static List<Map<String, Object>> toInputItems(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return List.of(messageItem(ROLE_SYSTEM, List.of(textContent(TYPE_INPUT_TEXT, systemMessage.text()))));
        } else if (message instanceof UserMessage userMessage) {
            List<Map<String, Object>> contents = new ArrayList<>();
            for (Content content : userMessage.contents()) {
                contents.add(toInputContent(content));
            }
            return List.of(messageItem(ROLE_USER, contents));
        } else if (message instanceof AiMessage aiMessage) {
            return toInputItems(aiMessage);
        } else if (message instanceof ToolExecutionResultMessage toolResult) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(FIELD_TYPE, TYPE_FUNCTION_CALL_OUTPUT);
            item.put(FIELD_CALL_ID, toolResult.id());
            item.put(FIELD_OUTPUT, toolResult.text());
            return List.of(item);
        } else {
            throw new UnsupportedFeatureException("Unsupported message type: "
                    + message.getClass().getName()
                    + ". Only SystemMessage, UserMessage, AiMessage and ToolExecutionResultMessage are supported.");
        }
    }

    private static List<Map<String, Object>> toInputItems(AiMessage aiMessage) {
        List<Map<String, Object>> items = new ArrayList<>();

        // Grok reasoning models need their encrypted reasoning back to continue a stateless conversation
        String encryptedContent = aiMessage.attribute(ENCRYPTED_REASONING_KEY, String.class);
        if (encryptedContent != null) {
            Map<String, Object> reasoning = new LinkedHashMap<>();
            reasoning.put(FIELD_TYPE, TYPE_REASONING);
            reasoning.put(FIELD_ENCRYPTED_CONTENT, encryptedContent);
            List<Map<String, Object>> summary = new ArrayList<>();
            if (aiMessage.thinking() != null && !aiMessage.thinking().isEmpty()) {
                summary.add(textContent(TYPE_SUMMARY_TEXT, aiMessage.thinking()));
            }
            reasoning.put(FIELD_SUMMARY, summary);
            items.add(reasoning);
        }

        if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
            items.add(messageItem(ROLE_ASSISTANT, List.of(textContent(TYPE_OUTPUT_TEXT, aiMessage.text()))));
        }

        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
            Map<String, Object> functionCall = new LinkedHashMap<>();
            functionCall.put(FIELD_TYPE, TYPE_FUNCTION_CALL);
            functionCall.put(FIELD_CALL_ID, toolExecutionRequest.id());
            functionCall.put(FIELD_NAME, toolExecutionRequest.name());
            functionCall.put(FIELD_ARGUMENTS, toolExecutionRequest.arguments());
            items.add(functionCall);
        }

        return items;
    }

    private static Map<String, Object> toInputContent(Content content) {
        if (content instanceof TextContent textContent) {
            return textContent(TYPE_INPUT_TEXT, textContent.text());
        } else if (content instanceof ImageContent imageContent) {
            Map<String, Object> image = new LinkedHashMap<>();
            image.put(FIELD_TYPE, TYPE_INPUT_IMAGE);
            image.put(FIELD_IMAGE_URL, toImageUrl(imageContent.image()));
            image.put(FIELD_DETAIL, toDetailString(imageContent.detailLevel()));
            return image;
        } else {
            throw new UnsupportedFeatureException("Unsupported content type: "
                    + content.getClass().getName() + ". Only TextContent and ImageContent are supported.");
        }
    }

    private static Map<String, Object> messageItem(String role, List<Map<String, Object>> contents) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put(FIELD_TYPE, TYPE_MESSAGE);
        item.put(FIELD_ROLE, role);
        item.put(FIELD_CONTENT, contents);
        return item;
    }

    private static Map<String, Object> textContent(String type, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put(FIELD_TYPE, type);
        content.put(FIELD_TEXT, text);
        return content;
    }

    private static String toImageUrl(Image image) {
        if (image.url() != null) {
            return image.url().toString();
        } else if (image.base64Data() != null) {
            String mimeType = getOrDefault(image.mimeType(), DEFAULT_IMAGE_MIME_TYPE);
            return "data:" + mimeType + ";base64," + image.base64Data();
        } else {
            throw new IllegalArgumentException("Image must have either url or base64Data");
        }
    }

    private static String toDetailString(ImageContent.DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> "low";
            case HIGH -> "high";
            case AUTO -> "auto";
            default ->
                throw new UnsupportedFeatureException(
                        "DetailLevel " + detailLevel + " is not supported by xAI. Supported values: LOW, HIGH, AUTO");
        };
    }

    private static Map<String, Object> toTool(ToolSpecification toolSpecification) {
        boolean strict = isEffectivelyStrict(toolSpecification, false);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put(FIELD_TYPE, TYPE_FUNCTION);
        tool.put(FIELD_NAME, toolSpecification.name());
        if (toolSpecification.description() != null) {
            tool.put(FIELD_DESCRIPTION, toolSpecification.description());
        }

        Map<String, Object> functionParameters;
        if (toolSpecification.parameters() != null) {
            functionParameters = toMap(toolSpecification.parameters(), strict);
        } else {
            functionParameters = new LinkedHashMap<>();
            functionParameters.put(FIELD_TYPE, TYPE_OBJECT);
            functionParameters.put(FIELD_PROPERTIES, Map.of());
            functionParameters.put(FIELD_REQUIRED, List.of());
            if (strict) {
                functionParameters.put(FIELD_ADDITIONAL_PROPERTIES, false);
            }
        }
        tool.put(FIELD_PARAMETERS, functionParameters);
        if (strict) {
            tool.put(FIELD_STRICT, true);
        }
        return tool;
    }

    private static String toToolChoiceString(ToolChoice toolChoice) {
        return switch (toolChoice) {
            case AUTO -> "auto";
            case REQUIRED -> "required";
            case NONE -> "none";
        };
    }

    private static Map<String, Object> toTextConfig(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == ResponseFormatType.TEXT) {
            return null;
        }

        Map<String, Object> format = new LinkedHashMap<>();
        JsonSchema jsonSchema = responseFormat.jsonSchema();
        if (jsonSchema == null) {
            format.put(FIELD_TYPE, TYPE_JSON_OBJECT);
        } else {
            if (!(jsonSchema.rootElement() instanceof JsonObjectSchema
                    || jsonSchema.rootElement() instanceof JsonRawSchema)) {
                throw new IllegalArgumentException(
                        "For xAI, the root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                                + jsonSchema.rootElement().getClass());
            }
            format.put(FIELD_TYPE, TYPE_JSON_SCHEMA);
            if (jsonSchema.name() != null) {
                format.put(FIELD_NAME, jsonSchema.name());
            }
            format.put(FIELD_SCHEMA, toMap(jsonSchema.rootElement(), true));
            format.put(FIELD_STRICT, true);
        }
        return Map.of(FIELD_FORMAT, format);
    }

    // --- response mapping ---

    @SuppressWarnings("unchecked")
    private static ChatResponse parseChatResponse(SuccessfulHttpResponse rawHttpResponse) {
        Map<String, Object> response = CODEC.fromJson(rawHttpResponse.body(), Map.class);
        return chatResponseFrom(response, rawHttpResponse);
    }

    /**
     * Maps a Responses API "response" object: the body of a blocking call,
     * or the "response" of a streamed "response.completed" event.
     */
    static ChatResponse chatResponseFrom(Map<String, Object> response, SuccessfulHttpResponse rawHttpResponse) {

        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        String encryptedContent = null;
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();

        for (Object item : list(response.get(FIELD_OUTPUT))) {
            Map<String, Object> outputItem = map(item);
            String type = string(outputItem.get(FIELD_TYPE));
            if (TYPE_MESSAGE.equals(type)) {
                for (Object content : list(outputItem.get(FIELD_CONTENT))) {
                    if (TYPE_OUTPUT_TEXT.equals(string(map(content).get(FIELD_TYPE)))) {
                        text.append(getOrDefault(string(map(content).get(FIELD_TEXT)), ""));
                    }
                }
            } else if (TYPE_REASONING.equals(type)) {
                for (Object summary : list(outputItem.get(FIELD_SUMMARY))) {
                    if (TYPE_SUMMARY_TEXT.equals(string(map(summary).get(FIELD_TYPE)))) {
                        thinking.append(getOrDefault(string(map(summary).get(FIELD_TEXT)), ""));
                    }
                }
                if (outputItem.get(FIELD_ENCRYPTED_CONTENT) != null) {
                    encryptedContent = string(outputItem.get(FIELD_ENCRYPTED_CONTENT));
                }
            } else if (TYPE_FUNCTION_CALL.equals(type)) {
                String id = getOrDefault(string(outputItem.get(FIELD_CALL_ID)), string(outputItem.get(FIELD_ID)));
                toolExecutionRequests.add(ToolExecutionRequest.builder()
                        .id(id)
                        .name(string(outputItem.get(FIELD_NAME)))
                        .arguments(getOrDefault(string(outputItem.get(FIELD_ARGUMENTS)), "{}"))
                        .build());
            }
        }

        AiMessage.Builder aiMessageBuilder = AiMessage.builder()
                .text(text.isEmpty() ? null : text.toString())
                .thinking(thinking.isEmpty() ? null : thinking.toString())
                .toolExecutionRequests(toolExecutionRequests);
        if (encryptedContent != null) {
            aiMessageBuilder.attributes(Map.of(ENCRYPTED_REASONING_KEY, encryptedContent));
        }

        XAiChatResponseMetadata.Builder metadataBuilder = XAiChatResponseMetadata.builder()
                .id(string(response.get(FIELD_ID)))
                .modelName(string(response.get(FIELD_MODEL)))
                .tokenUsage(tokenUsageFrom(response.get(FIELD_USAGE)))
                .finishReason(finishReasonFrom(
                        string(response.get(FIELD_STATUS)),
                        string(map(response.get(FIELD_INCOMPLETE_DETAILS)).get(FIELD_REASON)),
                        !toolExecutionRequests.isEmpty()))
                .rawHttpResponse(rawHttpResponse);
        if (response.get(FIELD_CREATED_AT) instanceof Number createdAt) {
            metadataBuilder.createdAt(createdAt.longValue());
        }

        return ChatResponse.builder()
                .aiMessage(aiMessageBuilder.build())
                .metadata(metadataBuilder.build())
                .build();
    }

    private static TokenUsage tokenUsageFrom(Object usage) {
        if (usage == null) {
            return null;
        }
        Map<String, Object> usageMap = map(usage);
        return new TokenUsage(
                integer(usageMap.get(FIELD_INPUT_TOKENS)),
                integer(usageMap.get(FIELD_OUTPUT_TOKENS)),
                integer(usageMap.get(FIELD_TOTAL_TOKENS)));
    }

    static FinishReason finishReasonFrom(String status, String incompleteReason, boolean hasToolCalls) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "completed" -> hasToolCalls ? FinishReason.TOOL_EXECUTION : FinishReason.STOP;
            case "incomplete" ->
                "content_filter".equals(incompleteReason) ? FinishReason.CONTENT_FILTER : FinishReason.LENGTH;
            default -> FinishReason.OTHER;
        };
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    static String string(Object value) {
        return value == null || value instanceof Map || value instanceof List ? null : String.valueOf(value);
    }

    static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private Duration connectTimeout;
        private Duration readTimeout;
        private boolean logRequests;
        private boolean logResponses;
        private Integer streamingBufferSize;
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

        Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            this.logRequests = getOrDefault(logRequests, false);
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            this.logResponses = getOrDefault(logResponses, false);
            return this;
        }

        Builder streamingBufferSize(Integer streamingBufferSize) {
            this.streamingBufferSize = streamingBufferSize;
            return this;
        }

        Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        XAiClient build() {
            return new XAiClient(this);
        }
    }
}
