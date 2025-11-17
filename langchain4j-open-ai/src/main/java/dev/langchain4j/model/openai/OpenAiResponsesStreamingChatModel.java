package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validate;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateInclude;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateMaxOutputTokens;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateReasoningEffort;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateServiceTier;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateTemperature;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateTextVerbosity;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateTopLogprobs;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateTopP;
import static dev.langchain4j.model.openai.internal.OpenAiResponsesValidator.validateTruncation;

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
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiResponsesStreamingChatModel implements StreamingChatModel {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiResponsesStreamingChatModel.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    private static final String FIELD_FUNCTION = "function";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_PARAMETERS = "parameters";
    private static final String FIELD_PROPERTIES = "properties";
    private static final String FIELD_REQUIRED = "required";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_DELTA = "delta";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_IMAGE_URL = "image_url";
    private static final String FIELD_DETAIL = "detail";
    private static final String FIELD_ITEM = "item";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CALL_ID = "call_id";
    private static final String FIELD_ITEM_ID = "item_id";
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
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final Integer maxToolCalls;
    private final Boolean parallelToolCalls;
    private final String previousResponseId;
    private final Integer topLogprobs;
    private final String truncation;
    private final List<String> include;
    private final String serviceTier;
    private final String safetyIdentifier;
    private final String promptCacheKey;
    private final String promptCacheRetention;
    private final String reasoningEffort;
    private final String textVerbosity;
    private final Boolean streamIncludeObfuscation;
    private final Boolean strict;
    private final List<ChatModelListener> listeners;

    private OpenAiResponsesStreamingChatModel(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        this.httpClient = httpClientBuilder.build();

        this.baseUrl = getOrDefault(builder.baseUrl, "https://api.openai.com/v1");
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey");
        this.organizationId = builder.organizationId;
        this.modelName = Objects.requireNonNull(builder.modelName, "modelName");

        validateTemperature(builder.temperature);
        this.temperature = builder.temperature;

        validateTopP(builder.topP);
        this.topP = builder.topP;

        validateMaxOutputTokens(builder.maxOutputTokens);
        this.maxOutputTokens = builder.maxOutputTokens;

        this.maxToolCalls = builder.maxToolCalls;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.previousResponseId = builder.previousResponseId;

        validateTopLogprobs(builder.topLogprobs);
        this.topLogprobs = builder.topLogprobs;

        validateTruncation(builder.truncation);
        this.truncation = builder.truncation;

        validateInclude(builder.include);
        this.include = builder.include != null ? new ArrayList<>(builder.include) : null;

        validateServiceTier(builder.serviceTier);
        this.serviceTier = builder.serviceTier;

        this.safetyIdentifier = builder.safetyIdentifier;
        this.promptCacheKey = builder.promptCacheKey;
        this.promptCacheRetention = builder.promptCacheRetention;

        validateReasoningEffort(builder.reasoningEffort);
        this.reasoningEffort = builder.reasoningEffort;

        validateTextVerbosity(builder.textVerbosity);
        this.textVerbosity = builder.textVerbosity;

        this.streamIncludeObfuscation = builder.streamIncludeObfuscation;
        this.strict = builder.strict != null ? builder.strict : true;
        this.listeners = builder.listeners != null ? new ArrayList<>(builder.listeners) : new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        try {
            ChatRequestParameters parameters = chatRequest.parameters();
            validate(parameters);

            var input = new ArrayList<Map<String, Object>>();
            for (var msg : chatRequest.messages()) {
                input.addAll(toResponsesMessages(msg));
            }

            var payload = new HashMap<String, Object>();
            String effectiveModelName =
                    parameters != null && parameters.modelName() != null ? parameters.modelName() : modelName;
            payload.put(FIELD_MODEL, effectiveModelName);
            payload.put(FIELD_INPUT, input);
            payload.put(FIELD_STREAM, true);
            payload.put(FIELD_STORE, false);

            Double effectiveTemperature =
                    parameters != null && parameters.temperature() != null ? parameters.temperature() : temperature;
            if (effectiveTemperature != null) {
                payload.put(FIELD_TEMPERATURE, effectiveTemperature);
            }

            Double effectiveTopP = parameters != null && parameters.topP() != null ? parameters.topP() : topP;
            if (effectiveTopP != null) {
                payload.put(FIELD_TOP_P, effectiveTopP);
            }

            Integer requestMaxOutputTokens = parameters != null ? parameters.maxOutputTokens() : null;
            Integer effectiveMaxOutputTokens =
                    requestMaxOutputTokens != null ? requestMaxOutputTokens : maxOutputTokens;
            if (effectiveMaxOutputTokens != null) {
                int finalMaxOutputTokens = Math.max(effectiveMaxOutputTokens, 16);
                payload.put(FIELD_MAX_OUTPUT_TOKENS, finalMaxOutputTokens);
            }

            if (maxToolCalls != null) {
                payload.put(FIELD_MAX_TOOL_CALLS, maxToolCalls);
            }

            if (parallelToolCalls != null) {
                payload.put(FIELD_PARALLEL_TOOL_CALLS, parallelToolCalls);
            }

            if (previousResponseId != null) {
                payload.put(FIELD_PREVIOUS_RESPONSE_ID, previousResponseId);
            }

            if (topLogprobs != null) {
                payload.put(FIELD_TOP_LOGPROBS, topLogprobs);
            }

            if (truncation != null && !truncation.isEmpty()) {
                payload.put(FIELD_TRUNCATION, truncation);
            }

            if (include != null && !include.isEmpty()) {
                payload.put(FIELD_INCLUDE, include);
            }

            if (serviceTier != null && !serviceTier.isEmpty()) {
                payload.put(FIELD_SERVICE_TIER, serviceTier);
            }

            if (safetyIdentifier != null) {
                payload.put(FIELD_SAFETY_IDENTIFIER, safetyIdentifier);
            }

            if (promptCacheKey != null) {
                payload.put(FIELD_PROMPT_CACHE_KEY, promptCacheKey);
            }

            if (promptCacheRetention != null) {
                payload.put(FIELD_PROMPT_CACHE_RETENTION, promptCacheRetention);
            }

            if (reasoningEffort != null && !reasoningEffort.isEmpty()) {
                var reasoning = new HashMap<String, Object>();
                reasoning.put(FIELD_EFFORT, reasoningEffort);
                payload.put(FIELD_REASONING, reasoning);
            }

            if (streamIncludeObfuscation != null) {
                var streamOptions = new HashMap<String, Object>();
                streamOptions.put(FIELD_INCLUDE_OBFUSCATION, streamIncludeObfuscation);
                payload.put(FIELD_STREAM_OPTIONS, streamOptions);
            }

            List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
            if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
                var tools = new ArrayList<Map<String, Object>>();
                for (var toolSpec : toolSpecifications) {
                    Map<String, Object> function = new HashMap<>();
                    function.put(FIELD_NAME, toolSpec.name());
                    if (toolSpec.description() != null) {
                        function.put(FIELD_DESCRIPTION, toolSpec.description());
                    }

                    Map<String, Object> functionParameters = null;
                    if (toolSpec.parameters() != null) {
                        functionParameters = toMap(toolSpec.parameters(), strict);
                    } else if (strict) {
                        functionParameters = new HashMap<>();
                        functionParameters.put(FIELD_TYPE, TYPE_OBJECT);
                        functionParameters.put(FIELD_PROPERTIES, new HashMap<>());
                        functionParameters.put(FIELD_ADDITIONAL_PROPERTIES, false);
                    }

                    if (functionParameters != null) {
                        function.put(FIELD_PARAMETERS, functionParameters);
                    }

                    if (strict) {
                        function.put(FIELD_STRICT, true);
                    }

                    var tool = new HashMap<String, Object>();
                    tool.put(FIELD_TYPE, TYPE_FUNCTION);
                    tool.put(FIELD_FUNCTION, function);
                    tools.add(tool);
                }
                payload.put(FIELD_TOOLS, tools);

                if (chatRequest.toolChoice() != null) {
                    payload.put(FIELD_TOOL_CHOICE, toToolChoiceString(chatRequest.toolChoice()));
                } else {
                    payload.put(FIELD_TOOL_CHOICE, "auto");
                }
            }

            var textConfig = toResponseTextConfig(chatRequest.responseFormat());
            if (textVerbosity != null) {
                if (textConfig == null) {
                    textConfig = new HashMap<>();
                }
                textConfig.put(FIELD_TEXT_VERBOSITY, textVerbosity);
            }
            if (textConfig != null) {
                payload.put(FIELD_TEXT, textConfig);
            }

            var requestBuilder = HttpRequest.builder()
                    .url(baseUrl + "/responses")
                    .method(HttpMethod.POST)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream");

            if (organizationId != null) {
                requestBuilder.addHeader(OPENAI_ORGANIZATION_HEADER, organizationId);
            }

            String requestBody = OBJECT_MAPPER.writeValueAsString(payload);
            logger.info("Sending OpenAI Responses payload: {}", requestBody);

            var request = requestBuilder.body(requestBody).build();

            httpClient.execute(request, new DefaultServerSentEventParser(), new ResponsesApiEventListener(handler));

        } catch (Exception e) {
            logger.error("Exception in doChat", e);
            handler.onError(e);
        }
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
                }
            }
            if (contentEntries.isEmpty()) {
                contentEntries.add(createInputTextContent(""));
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

            if (items.isEmpty()) {
                items.add(createMessageEntry(ROLE_ASSISTANT, List.of(createInputTextContent(""))));
            }
            return items;
        } else if (msg instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            var outputEntry = new HashMap<String, Object>();
            outputEntry.put(FIELD_TYPE, TYPE_FUNCTION_CALL_OUTPUT);
            outputEntry.put(FIELD_CALL_ID, toolExecutionResultMessage.id());
            outputEntry.put(FIELD_OUTPUT, toolExecutionResultMessage.text());
            return List.of(outputEntry);
        } else {
            return List.of(createMessageEntry(ROLE_USER, List.of(createInputTextContent(msg.toString()))));
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
        content.put(FIELD_IMAGE_URL, buildImageUrlPayload(image));
        content.put(FIELD_DETAIL, DETAIL_AUTO_VALUE);
        return content;
    }

    private Map<String, Object> buildImageUrlPayload(Image image) {
        var payload = new HashMap<String, Object>();
        payload.put("url", resolveImageUrl(image));
        return payload;
    }

    private String resolveImageUrl(Image image) {
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

    private Map<String, Object> toResponseTextConfig(dev.langchain4j.model.chat.request.ResponseFormat responseFormat) {
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

    public static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;
        private Integer maxToolCalls;
        private Boolean parallelToolCalls;
        private String previousResponseId;
        private Integer topLogprobs;
        private String truncation;
        private List<String> include;
        private String serviceTier;
        private String safetyIdentifier;
        private String promptCacheKey;
        private String promptCacheRetention;
        private String reasoningEffort;
        private String textVerbosity;
        private Boolean streamIncludeObfuscation;
        private Boolean strict;
        private List<ChatModelListener> listeners;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
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

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder maxToolCalls(Integer maxToolCalls) {
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

        public Builder topLogprobs(Integer topLogprobs) {
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

        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OpenAiResponsesStreamingChatModel build() {
            return new OpenAiResponsesStreamingChatModel(this);
        }
    }

    private static class ResponsesApiEventListener implements ServerSentEventListener {

        private final StreamingChatResponseHandler handler;
        private int eventCount = 0;
        private final Map<String, ToolExecutionRequest.Builder> toolCallBuilders = new HashMap<>();
        private final List<ToolExecutionRequest> completedToolCalls = new ArrayList<>();
        private final Set<String> completedToolCallItemIds = new HashSet<>();
        private final List<ServerSentEvent> rawServerSentEvents = new ArrayList<>();
        private SuccessfulHttpResponse rawHttpResponse;

        ResponsesApiEventListener(StreamingChatResponseHandler handler) {
            this.handler = handler;
        }

        @Override
        public void onOpen(SuccessfulHttpResponse response) {
            this.rawHttpResponse = response;
        }

        @Override
        public void onEvent(ServerSentEvent event) {
            eventCount++;
            rawServerSentEvents.add(event);
            var data = event.data();

            try {
                if (data == null || data.isEmpty()) {
                    return;
                }

                if (STREAM_DONE_MARKER.equals(data)) {
                    return;
                }

                handleDelta(data);
            } catch (Exception e) {
                logger.error("Error processing event #{}", eventCount, e);
                handler.onError(e);
            }
        }

        public void onComplete(Runnable onComplete) {
            onComplete.run();
        }

        @Override
        public void onError(Throwable error) {
            logger.error("SSE stream error", error);
            handler.onError(error);
        }

        private void handleDelta(String data) {
            if (!data.trim().startsWith("{") && !data.trim().startsWith("[")) {
                return;
            }

            logger.info("Received SSE event: {}", data);
            try {
                var node = OBJECT_MAPPER.readTree(data);
                var type = node.has(FIELD_TYPE) ? node.get(FIELD_TYPE).asText() : "";

                if (EVENT_OUTPUT_TEXT_DELTA.equals(type)) {
                    var text = node.path(FIELD_DELTA).asText();
                    if (!text.isEmpty()) {
                        handler.onPartialResponse(text);
                    }
                } else if (EVENT_OUTPUT_ITEM_ADDED.equals(type)) {
                    var item = node.path(FIELD_ITEM);
                    if (TYPE_FUNCTION_CALL.equals(item.path(FIELD_TYPE).asText())) {
                        var itemId = item.path(FIELD_ID).asText();
                        toolCallBuilders.put(
                                itemId,
                                ToolExecutionRequest.builder()
                                        .id(item.path(FIELD_CALL_ID).asText())
                                        .name(item.path(FIELD_NAME).asText())
                                        .arguments(""));
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
                    var item = node.path(FIELD_ITEM);
                    if (TYPE_FUNCTION_CALL.equals(item.path(FIELD_TYPE).asText())) {
                        var itemId = item.path(FIELD_ID).asText();
                        var builder =
                                toolCallBuilders.computeIfAbsent(itemId, ignored -> ToolExecutionRequest.builder());

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
                } else if (EVENT_RESPONSE_COMPLETED.equals(type)) {
                    var sb = new StringBuilder();
                    var responseNode = node.path(FIELD_RESPONSE);
                    var output = responseNode.path(FIELD_OUTPUT);
                    if (output.isArray()) {
                        for (var item : output) {
                            if (TYPE_MESSAGE.equals(item.path(FIELD_TYPE).asText())) {
                                var content = item.path(FIELD_CONTENT);
                                if (content.isArray()) {
                                    for (var c : content) {
                                        if (TYPE_OUTPUT_TEXT.equals(
                                                c.path(FIELD_TYPE).asText())) {
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
                                .inputTokenCount(
                                        usageNode.path(FIELD_INPUT_TOKENS).asInt())
                                .outputTokenCount(
                                        usageNode.path(FIELD_OUTPUT_TOKENS).asInt())
                                .totalTokenCount(
                                        usageNode.path(FIELD_TOTAL_TOKENS).asInt());

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
                            metadataBuilder.created(
                                    responseNode.path(FIELD_CREATED).asLong());
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

                        var finishReason = determineFinishReason(
                                responseNode.path(FIELD_STATUS).asText(null));
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
                        handler.onCompleteResponse(responseBuilder.build());
                    }
                }
            } catch (Exception e) {
                logger.error("Error parsing JSON data: {}", data, e);
                handler.onError(e);
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
            completedToolCalls.add(builder.build());
            completedToolCallItemIds.add(itemId);
            toolCallBuilders.remove(itemId);
        }
    }
}
