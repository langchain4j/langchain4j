package dev.langchain4j.model.openai;

import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.getOrDefault;

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
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class OpenAiResponsesClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String OPENAI_ORGANIZATION_HEADER = "OpenAI-Organization";
    private static final String STREAM_DONE_MARKER = "[DONE]";

    private static final String EVENT_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    private static final String EVENT_OUTPUT_ITEM_ADDED = "response.output_item.added";
    private static final String EVENT_FUNCTION_CALL_ARGUMENTS_DELTA = "response.function_call_arguments.delta";
    private static final String EVENT_FUNCTION_CALL_ARGUMENTS_DONE = "response.function_call_arguments.done";
    private static final String EVENT_OUTPUT_ITEM_DONE = "response.output_item.done";
    private static final String EVENT_RESPONSE_COMPLETED = "response.completed";

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
    private static final String FIELD_OUTPUT = "output";
    private static final String FIELD_USAGE = "usage";
    private static final String FIELD_INPUT_TOKENS = "input_tokens";
    private static final String FIELD_OUTPUT_TOKENS = "output_tokens";
    private static final String FIELD_TOTAL_TOKENS = "total_tokens";
    private static final String FIELD_INPUT_TOKENS_DETAILS = "input_tokens_details";
    private static final String FIELD_CACHED_TOKENS = "cached_tokens";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_INPUT = "input";
    private static final String FIELD_STREAM = "stream";
    private static final String FIELD_STORE = "store";
    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_TOP_P = "top_p";
    private static final String FIELD_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String FIELD_MAX_TOOL_CALLS = "max_tool_calls";
    private static final String FIELD_PARALLEL_TOOL_CALLS = "parallel_tool_calls";
    private static final String FIELD_PREVIOUS_RESPONSE_ID = "previous_response_id";
    private static final String FIELD_TOP_LOGPROBS = "top_logprobs";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_TOOL_CHOICE = "tool_choice";
    private static final String FIELD_TRUNCATION = "truncation";
    private static final String FIELD_INCLUDE = "include";
    private static final String FIELD_SERVICE_TIER = "service_tier";
    private static final String FIELD_SAFETY_IDENTIFIER = "safety_identifier";
    private static final String FIELD_PROMPT_CACHE_KEY = "prompt_cache_key";
    private static final String FIELD_PROMPT_CACHE_RETENTION = "prompt_cache_retention";
    private static final String FIELD_REASONING = "reasoning";
    private static final String FIELD_EFFORT = "effort";
    private static final String FIELD_STRICT = "strict";
    private static final String FIELD_STREAM_OPTIONS = "stream_options";
    private static final String FIELD_INCLUDE_OBFUSCATION = "include_obfuscation";
    private static final String FIELD_TEXT_VERBOSITY = "verbosity";
    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_JSON_SCHEMA = "json_schema";
    private static final String FIELD_SCHEMA = "schema";
    private static final String FIELD_ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED = "created";
    private static final String FIELD_SYSTEM_FINGERPRINT = "system_fingerprint";
    private static final String DETAIL_AUTO_VALUE = "auto";
    private static final String DEFAULT_IMAGE_MIME_TYPE = "image/jpeg";

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String TYPE_FUNCTION = "function";
    private static final String TYPE_FUNCTION_CALL = "function_call";
    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_OUTPUT_TEXT = "output_text";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_INPUT_TEXT = "input_text";
    private static final String TYPE_INPUT_IMAGE = "input_image";
    private static final String TYPE_FUNCTION_CALL_OUTPUT = "function_call_output";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_JSON_SCHEMA = "json_schema";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String organizationId;

    OpenAiResponsesClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = httpClientBuilder.build();
        if (builder.logRequests || builder.logResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }
        this.baseUrl = getOrDefault(builder.baseUrl, DEFAULT_BASE_URL);
        this.apiKey = builder.apiKey;
        this.organizationId = builder.organizationId;
    }

    static Builder builder() {
        return new Builder();
    }

    void streamingChat(ChatRequest chatRequest, OpenAiResponsesConfig config, StreamingChatResponseHandler handler) {
        try {
            Map<String, Object> payload = buildRequestPayload(chatRequest, config);
            HttpRequest request = buildHttpRequest(payload);

            httpClient.execute(request, new DefaultServerSentEventParser(), new ResponsesApiEventListener(handler));

        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(ExceptionMapper.DEFAULT.mapException(e)));
        }
    }

    private Map<String, Object> buildRequestPayload(ChatRequest chatRequest, OpenAiResponsesConfig config) {
        ChatRequestParameters parameters = chatRequest.parameters();

        var input = new ArrayList<Map<String, Object>>();
        for (var msg : chatRequest.messages()) {
            input.addAll(toResponsesMessages(msg));
        }

        var payload = new HashMap<String, Object>();
        String effectiveModelName =
                parameters != null && parameters.modelName() != null ? parameters.modelName() : config.modelName();
        payload.put(FIELD_MODEL, effectiveModelName);
        payload.put(FIELD_INPUT, input);
        payload.put(FIELD_STREAM, true);
        payload.put(FIELD_STORE, config.store());

        Double effectiveTemperature = parameters != null && parameters.temperature() != null
                ? parameters.temperature()
                : config.temperature();
        if (effectiveTemperature != null) {
            payload.put(FIELD_TEMPERATURE, effectiveTemperature);
        }

        Double effectiveTopP = parameters != null && parameters.topP() != null ? parameters.topP() : config.topP();
        if (effectiveTopP != null) {
            payload.put(FIELD_TOP_P, effectiveTopP);
        }

        Integer requestMaxOutputTokens = parameters != null ? parameters.maxOutputTokens() : null;
        Integer effectiveMaxOutputTokens =
                requestMaxOutputTokens != null ? requestMaxOutputTokens : config.maxOutputTokens();
        if (effectiveMaxOutputTokens != null) {
            payload.put(FIELD_MAX_OUTPUT_TOKENS, effectiveMaxOutputTokens);
        }

        if (config.maxToolCalls() != null) {
            payload.put(FIELD_MAX_TOOL_CALLS, config.maxToolCalls());
        }

        if (config.parallelToolCalls() != null) {
            payload.put(FIELD_PARALLEL_TOOL_CALLS, config.parallelToolCalls());
        }

        if (config.previousResponseId() != null) {
            payload.put(FIELD_PREVIOUS_RESPONSE_ID, config.previousResponseId());
        }

        if (config.topLogprobs() != null) {
            payload.put(FIELD_TOP_LOGPROBS, config.topLogprobs());
        }

        if (config.truncation() != null && !config.truncation().isEmpty()) {
            payload.put(FIELD_TRUNCATION, config.truncation());
        }

        if (config.include() != null && !config.include().isEmpty()) {
            payload.put(FIELD_INCLUDE, config.include());
        }

        if (config.serviceTier() != null && !config.serviceTier().isEmpty()) {
            payload.put(FIELD_SERVICE_TIER, config.serviceTier());
        }

        if (config.safetyIdentifier() != null) {
            payload.put(FIELD_SAFETY_IDENTIFIER, config.safetyIdentifier());
        }

        if (config.promptCacheKey() != null) {
            payload.put(FIELD_PROMPT_CACHE_KEY, config.promptCacheKey());
        }

        if (config.promptCacheRetention() != null) {
            payload.put(FIELD_PROMPT_CACHE_RETENTION, config.promptCacheRetention());
        }

        if (config.reasoningEffort() != null && !config.reasoningEffort().isEmpty()) {
            var reasoning = new HashMap<String, Object>();
            reasoning.put(FIELD_EFFORT, config.reasoningEffort());
            payload.put(FIELD_REASONING, reasoning);
        }

        if (config.streamIncludeObfuscation() != null) {
            var streamOptions = new HashMap<String, Object>();
            streamOptions.put(FIELD_INCLUDE_OBFUSCATION, config.streamIncludeObfuscation());
            payload.put(FIELD_STREAM_OPTIONS, streamOptions);
        }

        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            var tools = new ArrayList<Map<String, Object>>();
            for (var toolSpec : toolSpecifications) {
                var tool = new HashMap<String, Object>();
                tool.put(FIELD_TYPE, TYPE_FUNCTION);
                tool.put(FIELD_NAME, toolSpec.name());
                if (toolSpec.description() != null) {
                    tool.put(FIELD_DESCRIPTION, toolSpec.description());
                }

                Map<String, Object> functionParameters = null;
                if (toolSpec.parameters() != null) {
                    functionParameters = toMap(toolSpec.parameters(), config.strict());
                } else if (config.strict()) {
                    functionParameters = new HashMap<>();
                    functionParameters.put(FIELD_TYPE, TYPE_OBJECT);
                    functionParameters.put(FIELD_PROPERTIES, new HashMap<>());
                    functionParameters.put(FIELD_ADDITIONAL_PROPERTIES, false);
                }

                if (functionParameters != null) {
                    tool.put(FIELD_PARAMETERS, functionParameters);
                }

                if (config.strict()) {
                    tool.put(FIELD_STRICT, true);
                }

                tools.add(tool);
            }
            payload.put(FIELD_TOOLS, tools);

            if (chatRequest.toolChoice() != null) {
                payload.put(FIELD_TOOL_CHOICE, toToolChoiceString(chatRequest.toolChoice()));
            } else {
                payload.put(FIELD_TOOL_CHOICE, "auto");
            }
        }

        var textConfig = toResponseTextConfig(chatRequest.responseFormat(), config.strict());
        if (config.textVerbosity() != null) {
            if (textConfig == null) {
                textConfig = new HashMap<>();
            }
            textConfig.put(FIELD_TEXT_VERBOSITY, config.textVerbosity());
        }
        if (textConfig != null) {
            payload.put(FIELD_TEXT, textConfig);
        }

        return payload;
    }

    private HttpRequest buildHttpRequest(Map<String, Object> payload) throws Exception {
        String requestBody = OBJECT_MAPPER.writeValueAsString(payload);

        HttpRequest.Builder requestBuilder = HttpRequest.builder()
                .url(baseUrl + "/responses")
                .method(HttpMethod.POST)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream");

        if (organizationId != null) {
            requestBuilder.addHeader(OPENAI_ORGANIZATION_HEADER, organizationId);
        }

        return requestBuilder.body(requestBody).build();
    }

    private List<Map<String, Object>> toResponsesMessages(ChatMessage msg) {
        if (msg instanceof SystemMessage systemMessage) {
            return List.of(createMessageEntry(ROLE_SYSTEM, List.of(createInputTextContent(systemMessage.text()))));
        } else if (msg instanceof UserMessage userMessage) {
            List<Map<String, Object>> contentEntries = new ArrayList<>();
            for (Content content : userMessage.contents()) {
                if (content instanceof TextContent textContent) {
                    contentEntries.add(createInputTextContent(textContent.text()));
                } else if (content instanceof ImageContent imageContent) {
                    contentEntries.add(createInputImageContent(imageContent.image()));
                } else {
                    throw new UnsupportedFeatureException("Unsupported content type: "
                            + content.getClass().getName() + ". Only TextContent and ImageContent are supported.");
                }
            }
            return List.of(createMessageEntry(ROLE_USER, contentEntries));
        } else if (msg instanceof AiMessage aiMessage) {
            List<Map<String, Object>> items = new ArrayList<>();

            var text = aiMessage.text();
            if (text != null && !text.isEmpty()) {
                items.add(createMessageEntry(ROLE_ASSISTANT, List.of(createInputTextContent(text))));
            }

            if (aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    var functionCall = new HashMap<String, Object>();
                    functionCall.put(FIELD_TYPE, TYPE_FUNCTION_CALL);
                    functionCall.put(FIELD_CALL_ID, toolRequest.id());
                    functionCall.put(FIELD_NAME, toolRequest.name());
                    functionCall.put(FIELD_ARGUMENTS, toolRequest.arguments());
                    items.add(functionCall);
                }
            }

            return items;
        } else if (msg instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            var outputEntry = new HashMap<String, Object>();
            outputEntry.put(FIELD_TYPE, TYPE_FUNCTION_CALL_OUTPUT);
            outputEntry.put(FIELD_CALL_ID, toolExecutionResultMessage.id());
            outputEntry.put(FIELD_OUTPUT, toolExecutionResultMessage.text());
            return List.of(outputEntry);
        } else {
            throw new UnsupportedFeatureException(
                    "Unsupported message type: " + msg.getClass().getName()
                            + ". Only SystemMessage, UserMessage, AiMessage, and ToolExecutionResultMessage are supported.");
        }
    }

    private Map<String, Object> createMessageEntry(String role, List<Map<String, Object>> contentEntries) {
        var entry = new HashMap<String, Object>();
        entry.put(FIELD_TYPE, TYPE_MESSAGE);
        entry.put(FIELD_ROLE, role);
        entry.put(FIELD_CONTENT, contentEntries);
        return entry;
    }

    private Map<String, Object> createInputTextContent(String text) {
        var content = new HashMap<String, Object>();
        content.put(FIELD_TYPE, TYPE_INPUT_TEXT);
        content.put(FIELD_TEXT, text);
        return content;
    }

    private Map<String, Object> createInputImageContent(Image image) {
        var content = new HashMap<String, Object>();
        content.put(FIELD_TYPE, TYPE_INPUT_IMAGE);
        content.put(FIELD_IMAGE_URL, buildImageUrl(image));
        content.put(FIELD_DETAIL, DETAIL_AUTO_VALUE);
        return content;
    }

    private String buildImageUrl(Image image) {
        if (image.url() != null) {
            return image.url().toString();
        } else if (image.base64Data() != null) {
            String mimeType = image.mimeType() != null ? image.mimeType() : DEFAULT_IMAGE_MIME_TYPE;
            return "data:" + mimeType + ";base64," + image.base64Data();
        } else {
            throw new IllegalArgumentException("Image must have either url or base64Data");
        }
    }

    private String toToolChoiceString(dev.langchain4j.model.chat.request.ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        return switch (toolChoice) {
            case AUTO -> "auto";
            case REQUIRED -> "required";
            case NONE -> "none";
        };
    }

    private Map<String, Object> toResponseTextConfig(
            dev.langchain4j.model.chat.request.ResponseFormat responseFormat, boolean strict) {
        if (responseFormat == null
                || responseFormat.type() == dev.langchain4j.model.chat.request.ResponseFormatType.TEXT) {
            return null;
        }

        var textConfig = new HashMap<String, Object>();
        dev.langchain4j.model.chat.request.json.JsonSchema jsonSchema = responseFormat.jsonSchema();

        if (jsonSchema == null) {
            var format = new HashMap<String, Object>();
            format.put(FIELD_TYPE, TYPE_JSON_OBJECT);
            textConfig.put(FIELD_FORMAT, format);
        } else {
            if (!(jsonSchema.rootElement() instanceof dev.langchain4j.model.chat.request.json.JsonObjectSchema
                    || jsonSchema.rootElement() instanceof dev.langchain4j.model.chat.request.json.JsonRawSchema)) {
                throw new IllegalArgumentException(
                        "For OpenAI Responses API, the root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                                + jsonSchema.rootElement().getClass());
            }

            Map<String, Object> schemaMap = toMap(jsonSchema.rootElement(), strict);
            var format = new HashMap<String, Object>();
            format.put(FIELD_TYPE, TYPE_JSON_SCHEMA);
            if (jsonSchema.name() != null) {
                format.put(FIELD_NAME, jsonSchema.name());
            }
            var jsonSchemaConfig = new HashMap<String, Object>();
            jsonSchemaConfig.put(FIELD_SCHEMA, schemaMap);
            jsonSchemaConfig.put(FIELD_STRICT, strict);
            if (jsonSchema.name() != null) {
                jsonSchemaConfig.put(FIELD_NAME, jsonSchema.name());
            }
            format.put(FIELD_SCHEMA, schemaMap);
            format.put(FIELD_JSON_SCHEMA, jsonSchemaConfig);
            textConfig.put(FIELD_FORMAT, format);
        }

        return textConfig;
    }

    static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private boolean logRequests;
        private boolean logResponses;

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

        Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            if (logRequests != null) {
                this.logRequests = logRequests;
            }
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            if (logResponses != null) {
                this.logResponses = logResponses;
            }
            return this;
        }

        OpenAiResponsesClient build() {
            return new OpenAiResponsesClient(this);
        }
    }

    private static class ResponsesApiEventListener implements ServerSentEventListener {

        private final StreamingChatResponseHandler handler;
        private volatile StreamingHandle streamingHandle;
        private final Map<String, ToolExecutionRequest.Builder> toolCallBuilders = new HashMap<>();
        private final Map<String, Integer> toolCallIndices = new HashMap<>();
        private final List<ToolExecutionRequest> completedToolCalls = new ArrayList<>();
        private final Set<String> completedToolCallItemIds = new HashSet<>();
        private final List<ServerSentEvent> rawServerSentEvents = new ArrayList<>();
        private SuccessfulHttpResponse rawHttpResponse;

        ResponsesApiEventListener(StreamingChatResponseHandler handler) {
            this.handler = handler;
        }

        private boolean isCancelled() {
            return streamingHandle != null && streamingHandle.isCancelled();
        }

        private void assignIndexIfAbsent(String itemId, int index) {
            toolCallIndices.putIfAbsent(itemId, index);
        }

        @Override
        public void onOpen(SuccessfulHttpResponse response) {
            this.rawHttpResponse = response;
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

            if (isCancelled()) {
                return;
            }
            rawServerSentEvents.add(event);
            var data = event.data();

            if (data == null || data.isEmpty()) {
                return;
            }

            if (STREAM_DONE_MARKER.equals(data)) {
                return;
            }

            handleDelta(data);
        }

        @Override
        public void onError(Throwable error) {
            withLoggingExceptions(() -> handler.onError(ExceptionMapper.DEFAULT.mapException(error)));
        }

        private void handleDelta(String data) {
            if (!data.trim().startsWith("{") && !data.trim().startsWith("[")) {
                return;
            }

            try {
                var node = OBJECT_MAPPER.readTree(data);
                var type = node.has(FIELD_TYPE) ? node.get(FIELD_TYPE).asText() : "";

                if (EVENT_OUTPUT_TEXT_DELTA.equals(type)) {
                    var text = node.path(FIELD_DELTA).asText();
                    if (!text.isEmpty()) {
                        InternalStreamingChatResponseHandlerUtils.onPartialResponse(handler, text, streamingHandle);
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
                        assignIndexIfAbsent(itemId, outputIndex);
                    }
                } else if (EVENT_FUNCTION_CALL_ARGUMENTS_DELTA.equals(type)) {
                    var itemId = node.path(FIELD_ITEM_ID).asText();
                    var builder = toolCallBuilders.get(itemId);
                    if (builder != null) {
                        var currentArgs = builder.build().arguments();
                        builder.arguments(currentArgs + node.path(FIELD_DELTA).asText());
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
                } else if (EVENT_RESPONSE_COMPLETED.equals(type)) {
                    handleResponseCompleted(node);
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
                var builder = toolCallBuilders.computeIfAbsent(itemId, ignored -> ToolExecutionRequest.builder());
                assignIndexIfAbsent(itemId, outputIndex);

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
            var sb = new StringBuilder();
            var responseNode = node.path(FIELD_RESPONSE);
            var output = responseNode.path(FIELD_OUTPUT);
            if (output.isArray()) {
                for (var item : output) {
                    if (TYPE_MESSAGE.equals(item.path(FIELD_TYPE).asText())) {
                        var content = item.path(FIELD_CONTENT);
                        if (content.isArray()) {
                            for (var c : content) {
                                if (TYPE_OUTPUT_TEXT.equals(c.path(FIELD_TYPE).asText())) {
                                    sb.append(c.path(FIELD_TEXT).asText());
                                }
                            }
                        }
                    }
                }
            }

            OpenAiTokenUsage tokenUsage = null;
            var usageNode = responseNode.path(FIELD_USAGE);
            if (!usageNode.isMissingNode()) {
                var usageBuilder = OpenAiTokenUsage.builder()
                        .inputTokenCount(usageNode.path(FIELD_INPUT_TOKENS).asInt())
                        .outputTokenCount(usageNode.path(FIELD_OUTPUT_TOKENS).asInt())
                        .totalTokenCount(usageNode.path(FIELD_TOTAL_TOKENS).asInt());

                var inputDetailsNode = usageNode.path(FIELD_INPUT_TOKENS_DETAILS);
                if (!inputDetailsNode.isMissingNode()) {
                    var cachedTokens =
                            inputDetailsNode.path(FIELD_CACHED_TOKENS).asInt();
                    if (cachedTokens > 0) {
                        usageBuilder.inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                                .cachedTokens(cachedTokens)
                                .build());
                    }
                }

                tokenUsage = usageBuilder.build();
            }

            if (!sb.isEmpty() || !completedToolCalls.isEmpty()) {
                var text = sb.isEmpty() ? null : sb.toString();
                var aiMessage = !completedToolCalls.isEmpty() && text != null
                        ? new AiMessage(text, completedToolCalls)
                        : !completedToolCalls.isEmpty()
                                ? AiMessage.from(completedToolCalls)
                                : new AiMessage(sb.toString());

                var responseBuilder = ChatResponse.builder().aiMessage(aiMessage);
                var metadataBuilder = OpenAiChatResponseMetadata.builder()
                        .id(responseNode.path(FIELD_ID).asText(null))
                        .modelName(responseNode.path(FIELD_MODEL).asText(null));

                if (responseNode.hasNonNull(FIELD_CREATED)) {
                    metadataBuilder.created(responseNode.path(FIELD_CREATED).asLong());
                }
                if (responseNode.hasNonNull(FIELD_SERVICE_TIER)) {
                    metadataBuilder.serviceTier(
                            responseNode.path(FIELD_SERVICE_TIER).asText());
                }
                if (responseNode.hasNonNull(FIELD_SYSTEM_FINGERPRINT)) {
                    metadataBuilder.systemFingerprint(
                            responseNode.path(FIELD_SYSTEM_FINGERPRINT).asText());
                }
                if (tokenUsage != null) {
                    metadataBuilder.tokenUsage(tokenUsage);
                }

                var finishReason =
                        determineFinishReason(responseNode.path(FIELD_STATUS).asText(null));
                if (finishReason != null) {
                    metadataBuilder.finishReason(finishReason);
                }
                if (rawHttpResponse != null) {
                    metadataBuilder.rawHttpResponse(rawHttpResponse);
                }
                if (!rawServerSentEvents.isEmpty()) {
                    metadataBuilder.rawServerSentEvents(new ArrayList<>(rawServerSentEvents));
                }

                responseBuilder.metadata(metadataBuilder.build());
                if (!isCancelled()) {
                    try {
                        handler.onCompleteResponse(responseBuilder.build());
                    } catch (Exception e) {
                        withLoggingExceptions(() -> handler.onError(e));
                    }
                }
            }
        }

        private FinishReason determineFinishReason(String status) {
            if (status == null || status.isBlank()) {
                return null;
            }
            return switch (status) {
                case "completed" -> !completedToolCalls.isEmpty() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP;
                case "incomplete" -> FinishReason.LENGTH;
                case "failed" -> FinishReason.OTHER;
                default -> FinishReason.OTHER;
            };
        }

        private void completeToolCall(String itemId, ToolExecutionRequest.Builder builder) {
            if (builder == null || completedToolCallItemIds.contains(itemId)) {
                return;
            }
            ToolExecutionRequest toolExecutionRequest = builder.build();
            completedToolCalls.add(toolExecutionRequest);
            completedToolCallItemIds.add(itemId);
            toolCallBuilders.remove(itemId);
            Integer index = toolCallIndices.remove(itemId);
            int safeIndex = index != null ? index : completedToolCalls.size() - 1;
            if (!isCancelled()) {
                InternalStreamingChatResponseHandlerUtils.onCompleteToolCall(
                        handler, new CompleteToolCall(safeIndex, toolExecutionRequest));
            }
        }
    }
}
