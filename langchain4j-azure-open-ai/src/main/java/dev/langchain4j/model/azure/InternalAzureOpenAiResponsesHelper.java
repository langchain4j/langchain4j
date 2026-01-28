package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import com.azure.ai.openai.implementation.OpenAIUtils;
import com.azure.ai.openai.responses.AzureResponsesServiceVersion;
import com.azure.ai.openai.responses.ResponsesAsyncClient;
import com.azure.ai.openai.responses.ResponsesClient;
import com.azure.ai.openai.responses.ResponsesClientBuilder;
import com.azure.ai.openai.responses.implementation.NonAzureResponsesClientImpl;
import com.azure.ai.openai.responses.implementation.OpenAIServerSentEvents;
import com.azure.ai.openai.responses.implementation.ResponsesClientImpl;
import com.azure.ai.openai.responses.models.CreateResponsesRequestModel;
import com.azure.ai.openai.responses.models.ResponseTextOptions;
import com.azure.ai.openai.responses.models.ResponsesAssistantMessage;
import com.azure.ai.openai.responses.models.ResponsesContent;
import com.azure.ai.openai.responses.models.ResponsesFunctionCallItem;
import com.azure.ai.openai.responses.models.ResponsesFunctionTool;
import com.azure.ai.openai.responses.models.ResponsesInputContentImage;
import com.azure.ai.openai.responses.models.ResponsesInputContentImageDetail;
import com.azure.ai.openai.responses.models.ResponsesInputContentText;
import com.azure.ai.openai.responses.models.ResponsesItem;
import com.azure.ai.openai.responses.models.ResponsesMessage;
import com.azure.ai.openai.responses.models.ResponsesMessageRole;
import com.azure.ai.openai.responses.models.ResponsesOutputContentText;
import com.azure.ai.openai.responses.models.ResponsesReasoningConfigurationEffort;
import com.azure.ai.openai.responses.models.ResponsesReasoningItem;
import com.azure.ai.openai.responses.models.ResponsesReasoningItemSummaryElement;
import com.azure.ai.openai.responses.models.ResponsesReasoningItemSummaryElementSummaryText;
import com.azure.ai.openai.responses.models.ResponsesResponse;
import com.azure.ai.openai.responses.models.ResponsesResponseIncompleteDetails;
import com.azure.ai.openai.responses.models.ResponsesResponseIncompleteDetailsReason;
import com.azure.ai.openai.responses.models.ResponsesResponseStatus;
import com.azure.ai.openai.responses.models.ResponsesResponseUsage;
import com.azure.ai.openai.responses.models.ResponsesStreamEvent;
import com.azure.ai.openai.responses.models.ResponsesSystemMessage;
import com.azure.ai.openai.responses.models.ResponsesTextFormatJsonObject;
import com.azure.ai.openai.responses.models.ResponsesTextFormatJsonSchema;
import com.azure.ai.openai.responses.models.ResponsesTextFormatText;
import com.azure.ai.openai.responses.models.ResponsesTool;
import com.azure.ai.openai.responses.models.ResponsesToolChoiceOption;
import com.azure.ai.openai.responses.models.ResponsesUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientProvider;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.core.http.rest.RequestOptions;
import com.azure.core.util.BinaryData;
import com.azure.core.util.ClientOptions;
import com.azure.core.util.Header;
import com.azure.core.util.HttpClientOptions;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Internal
class InternalAzureOpenAiResponsesHelper {

    static final String DEFAULT_USER_AGENT = InternalAzureOpenAiHelper.DEFAULT_USER_AGENT;

    static ResponsesClient setupSyncClient(
            String endpoint,
            String serviceVersion,
            Object credential,
            Duration timeout,
            Integer maxRetries,
            RetryOptions retryOptions,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {
        ResponsesClientBuilder builder = setupResponsesClientBuilder(
                endpoint,
                serviceVersion,
                credential,
                timeout,
                maxRetries,
                retryOptions,
                httpClientProvider,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
        return builder.buildClient();
    }

    static ResponsesAsyncClient setupAsyncClient(
            String endpoint,
            String serviceVersion,
            Object credential,
            Duration timeout,
            Integer maxRetries,
            RetryOptions retryOptions,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {
        ResponsesClientBuilder builder = setupResponsesClientBuilder(
                endpoint,
                serviceVersion,
                credential,
                timeout,
                maxRetries,
                retryOptions,
                httpClientProvider,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
        return builder.buildAsyncClient();
    }

    private static ResponsesClientBuilder setupResponsesClientBuilder(
            String endpoint,
            String serviceVersion,
            Object credential,
            Duration timeout,
            Integer maxRetries,
            RetryOptions retryOptions,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {
        timeout = getOrDefault(timeout, ofSeconds(60));
        HttpClientOptions clientOptions = new HttpClientOptions();
        clientOptions.setConnectTimeout(timeout);
        clientOptions.setResponseTimeout(timeout);
        clientOptions.setReadTimeout(timeout);
        clientOptions.setWriteTimeout(timeout);
        clientOptions.setProxyOptions(proxyOptions);

        String userAgent = DEFAULT_USER_AGENT;
        if (userAgentSuffix != null && !userAgentSuffix.isEmpty()) {
            userAgent = DEFAULT_USER_AGENT + "-" + userAgentSuffix;
        }
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("User-Agent", userAgent));
        if (customHeaders != null) {
            customHeaders.forEach((name, value) -> headers.add(new Header(name, value)));
        }
        clientOptions.setHeaders(headers);

        httpClientProvider = getOrDefault(httpClientProvider, NettyAsyncHttpClientProvider::new);
        HttpClient httpClient = httpClientProvider.createInstance(clientOptions);

        HttpLogOptions httpLogOptions = new HttpLogOptions();
        if (logRequestsAndResponses) {
            httpLogOptions.setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS);
        }

        retryOptions = InternalAzureOpenAiHelper.resolveRetryOptions(maxRetries, retryOptions);

        ClientOptions responsesClientOptions = new ClientOptions().setHeaders(headers);

        ResponsesClientBuilder builder = new ResponsesClientBuilder()
                .endpoint(ensureNotBlank(endpoint, "endpoint"))
                .serviceVersion(getResponsesServiceVersion(serviceVersion))
                .httpClient(httpClient)
                .httpLogOptions(httpLogOptions)
                .clientOptions(responsesClientOptions)
                .retryOptions(retryOptions);

        if (credential instanceof String) {
            builder.credential(new AzureKeyCredential((String) credential));
        } else if (credential instanceof KeyCredential) {
            builder.credential((KeyCredential) credential);
        } else if (credential instanceof TokenCredential) {
            builder.credential((TokenCredential) credential);
        } else {
            throw new IllegalArgumentException("Unsupported credential type: " + credential.getClass());
        }

        return builder;
    }

    static AzureResponsesServiceVersion getResponsesServiceVersion(String serviceVersion) {
        if (serviceVersion != null) {
            for (AzureResponsesServiceVersion version : AzureResponsesServiceVersion.values()) {
                if (version.getVersion().equals(serviceVersion)) {
                    return version;
                }
            }
        }
        return AzureResponsesServiceVersion.getLatest();
    }

    static List<ResponsesMessage> toResponsesMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(InternalAzureOpenAiResponsesHelper::toResponsesMessage)
                .collect(toList());
    }

    private static ResponsesMessage toResponsesMessage(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return new ResponsesSystemMessage(List.of(new ResponsesInputContentText(systemMessage.text())));
        } else if (message instanceof UserMessage userMessage) {
            return toUserMessage(userMessage);
        } else if (message instanceof AiMessage aiMessage) {
            return toAssistantMessage(aiMessage);
        } else if (message instanceof ToolExecutionResultMessage) {
            throw new UnsupportedFeatureException(
                    "Tool execution result messages are not supported by Azure Responses API in this SDK version");
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + message.type());
        }
    }

    private static ResponsesMessage toAssistantMessage(AiMessage aiMessage) {
        if (aiMessage.hasToolExecutionRequests()) {
            throw new UnsupportedFeatureException(
                    "Tool execution requests in assistant messages are not supported by Azure Responses API in this SDK version");
        }
        if (isNullOrBlank(aiMessage.text())) {
            throw new UnsupportedFeatureException(
                    "Assistant messages without text are not supported by Azure Responses API in this SDK version");
        }
        return new ResponsesAssistantMessage(List.of(new ResponsesInputContentText(aiMessage.text())));
    }

    private static ResponsesMessage toUserMessage(UserMessage userMessage) {
        if (userMessage.hasSingleText()) {
            return new ResponsesUserMessage(List.of(new ResponsesInputContentText(
                    ((TextContent) userMessage.contents().get(0)).text())));
        }
        List<ResponsesContent> contents = userMessage.contents().stream()
                .map(content -> {
                    if (content instanceof TextContent) {
                        return new ResponsesInputContentText(((TextContent) content).text());
                    } else if (content instanceof ImageContent imageContent) {
                        ResponsesInputContentImage image = new ResponsesInputContentImage()
                                .setImageUrl(toImageUrl(imageContent.image()))
                                .setDetail(toImageDetail(imageContent.detailLevel()));
                        return image;
                    } else {
                        throw new IllegalArgumentException("Unsupported content type: " + content.type());
                    }
                })
                .collect(toList());
        return new ResponsesUserMessage(contents);
    }

    private static String toImageUrl(Image image) {
        if (image.url() != null) {
            return image.url().toString();
        }
        return String.format("data:%s;base64,%s", image.mimeType(), image.base64Data());
    }

    private static ResponsesInputContentImageDetail toImageDetail(ImageContent.DetailLevel detailLevel) {
        if (detailLevel == null) {
            return null;
        }
        return switch (detailLevel) {
            case LOW -> ResponsesInputContentImageDetail.LOW;
            case HIGH -> ResponsesInputContentImageDetail.HIGH;
            case AUTO -> ResponsesInputContentImageDetail.AUTO;
        };
    }

    static ResponseTextOptions toResponseTextOptions(ResponseFormat responseFormat, boolean strict) {
        if (responseFormat == null) {
            return null;
        }

        ResponseTextOptions options = new ResponseTextOptions();
        if (responseFormat.type() == ResponseFormatType.TEXT) {
            options.setFormat(new ResponsesTextFormatText());
        } else if (responseFormat.type() == ResponseFormatType.JSON) {
            JsonSchema jsonSchema = responseFormat.jsonSchema();
            if (jsonSchema == null) {
                options.setFormat(new ResponsesTextFormatJsonObject());
            } else {
                if (!(jsonSchema.rootElement() instanceof JsonObjectSchema
                        || jsonSchema.rootElement() instanceof JsonRawSchema)) {
                    throw new IllegalArgumentException(
                            "For Azure OpenAI Responses API, the root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                                    + jsonSchema.rootElement().getClass());
                }
                Map<String, Object> schemaMap = toMap(jsonSchema.rootElement(), strict);
                ResponsesTextFormatJsonSchema schema =
                        new ResponsesTextFormatJsonSchema(jsonSchema.name(), BinaryData.fromObject(schemaMap));
                schema.setStrict(strict);
                options.setFormat(schema);
            }
        }

        return options;
    }

    static List<ResponsesTool> toResponsesTools(Collection<ToolSpecification> toolSpecifications, boolean strict) {
        return toolSpecifications.stream()
                .map(toolSpecification -> new ResponsesFunctionTool(
                        toolSpecification.name(),
                        toolSpecification.description(),
                        getParameters(toolSpecification),
                        strict))
                .collect(toList());
    }

    static BinaryData toToolChoiceBinaryData(ToolChoice toolChoice) {
        ResponsesToolChoiceOption option =
                switch (toolChoice) {
                    case AUTO -> ResponsesToolChoiceOption.AUTO;
                    case REQUIRED -> ResponsesToolChoiceOption.REQUIRED;
                    case NONE -> ResponsesToolChoiceOption.NONE;
                };
        return BinaryData.fromObject(option);
    }

    static ResponsesToolChoiceOption toToolChoiceOption(ToolChoice toolChoice) {
        return switch (toolChoice) {
            case AUTO -> ResponsesToolChoiceOption.AUTO;
            case REQUIRED -> ResponsesToolChoiceOption.REQUIRED;
            case NONE -> ResponsesToolChoiceOption.NONE;
        };
    }

    static BinaryData buildRequestBody(
            String modelName,
            List<ResponsesMessage> messages,
            Double temperature,
            Double topP,
            Integer maxOutputTokens,
            String user,
            ResponseTextOptions textOptions,
            ResponsesReasoningConfigurationEffort reasoningEffort,
            String reasoningSummary,
            List<ResponsesTool> tools,
            ToolChoice toolChoice,
            boolean stream) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelName);
        payload.put("input", messages);

        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        if (topP != null) {
            payload.put("top_p", topP);
        }
        if (maxOutputTokens != null) {
            payload.put("max_output_tokens", maxOutputTokens);
        }
        if (!isNullOrBlank(user)) {
            payload.put("user", user);
        }
        if (textOptions != null) {
            payload.put("text", textOptions);
        }
        if (!isNullOrEmpty(tools)) {
            payload.put("tools", tools);
        }
        if (toolChoice != null) {
            payload.put("tool_choice", toToolChoiceOption(toolChoice));
        }

        if (reasoningEffort != null || reasoningSummary != null) {
            Map<String, Object> reasoning = new HashMap<>();
            if (reasoningEffort != null) {
                reasoning.put("effort", reasoningEffort.toString());
            }
            if (reasoningSummary != null) {
                reasoning.put("summary", reasoningSummary);
            }
            payload.put("reasoning", reasoning);
        }

        if (stream) {
            payload.put("stream", true);
        }

        return BinaryData.fromObject(payload);
    }

    static ResponsesResponse createResponseWithRawRequest(ResponsesClient client, BinaryData requestBody) {
        ResponsesClientImpl serviceClient = getServiceClient(client);
        NonAzureResponsesClientImpl nonAzureServiceClient = getNonAzureServiceClient(client);
        RequestOptions requestOptions = new RequestOptions();
        if (serviceClient != null) {
            OpenAIUtils.addAzureVersionToRequestOptions(
                    serviceClient.getEndpoint(), requestOptions, serviceClient.getServiceVersion());
            com.azure.core.http.rest.Response<BinaryData> response =
                    serviceClient.createResponseWithResponse("application/json", requestBody, requestOptions);
            return response.getValue().toObject(ResponsesResponse.class);
        }
        if (nonAzureServiceClient != null) {
            com.azure.core.http.rest.Response<BinaryData> response =
                    nonAzureServiceClient.createResponseWithResponse("application/json", requestBody, requestOptions);
            return response.getValue().toObject(ResponsesResponse.class);
        }
        throw new IllegalStateException("Unable to access Responses client implementation");
    }

    static Flux<ResponsesStreamEvent> createResponseStreamWithRawRequest(
            ResponsesAsyncClient client, BinaryData requestBody) {
        ResponsesClientImpl serviceClient = getServiceClient(client);
        NonAzureResponsesClientImpl nonAzureServiceClient = getNonAzureServiceClient(client);
        RequestOptions requestOptions = new RequestOptions();
        if (serviceClient != null) {
            OpenAIUtils.addAzureVersionToRequestOptions(
                    serviceClient.getEndpoint(), requestOptions, serviceClient.getServiceVersion());
            Mono<com.azure.core.http.rest.Response<BinaryData>> responseMono =
                    serviceClient.createResponseWithResponseAsync("text/event-stream", requestBody, requestOptions);
            Flux<ByteBuffer> events =
                    responseMono.flatMapMany(response -> response.getValue().toFluxByteBuffer());
            return new OpenAIServerSentEvents(events).getEvents();
        }
        if (nonAzureServiceClient != null) {
            Mono<com.azure.core.http.rest.Response<BinaryData>> responseMono =
                    nonAzureServiceClient.createResponseWithResponseAsync(
                            "text/event-stream", requestBody, requestOptions);
            Flux<ByteBuffer> events =
                    responseMono.flatMapMany(response -> response.getValue().toFluxByteBuffer());
            return new OpenAIServerSentEvents(events).getEvents();
        }
        throw new IllegalStateException("Unable to access Responses client implementation");
    }

    private static ResponsesClientImpl getServiceClient(Object client) {
        return getField(client, "serviceClient", ResponsesClientImpl.class);
    }

    private static NonAzureResponsesClientImpl getNonAzureServiceClient(Object client) {
        return getField(client, "nonAzureServiceClient", NonAzureResponsesClientImpl.class);
    }

    private static <T> T getField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            return null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Unable to access field '" + fieldName + "' on "
                            + target.getClass().getName(),
                    e);
        }
    }

    private static BinaryData getParameters(ToolSpecification toolSpecification) {
        return toOpenAiParameters(toolSpecification.parameters());
    }

    private static final Map<String, Object> NO_PARAMETER_DATA = new HashMap<>();

    static {
        NO_PARAMETER_DATA.put("type", "object");
        NO_PARAMETER_DATA.put("properties", new HashMap<>());
    }

    private static BinaryData toOpenAiParameters(JsonObjectSchema toolParameters) {
        Parameters parameters = new Parameters();
        if (toolParameters == null) {
            return BinaryData.fromObject(NO_PARAMETER_DATA);
        }
        parameters.setProperties(toMap(toolParameters.properties()));
        parameters.setRequired(toolParameters.required());
        return BinaryData.fromObject(parameters);
    }

    private static class Parameters {

        private final String type = "object";

        private Map<String, Map<String, Object>> properties = new HashMap<>();

        private List<String> required = new ArrayList<>();

        public String getType() {
            return this.type;
        }

        public Map<String, Map<String, Object>> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
        }

        public List<String> getRequired() {
            return required;
        }

        public void setRequired(List<String> required) {
            this.required = required;
        }
    }

    static Response<AiMessage> toResponse(ResponsesResponse response, String fallbackText) {
        String text = extractOutputText(response);
        if (isNullOrBlank(text)) {
            text = fallbackText;
        }
        String summary = extractReasoningSummary(response);
        List<ToolExecutionRequest> toolExecutionRequests = extractToolExecutionRequests(response);

        AiMessage aiMessage = AiMessage.builder()
                .text(isNullOrBlank(text) ? null : text)
                .thinking(isNullOrBlank(summary) ? null : summary)
                .toolExecutionRequests(toolExecutionRequests)
                .build();

        TokenUsage tokenUsage = tokenUsageFrom(response != null ? response.getUsage() : null);
        FinishReason finishReason = finishReasonFrom(response, toolExecutionRequests);

        return Response.from(aiMessage, tokenUsage, finishReason);
    }

    private static String extractOutputText(ResponsesResponse response) {
        if (response == null || isNullOrEmpty(response.getOutput())) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (ResponsesItem item : response.getOutput()) {
            if (item instanceof ResponsesMessage message) {
                if (message.getRole() != ResponsesMessageRole.ASSISTANT) {
                    continue;
                }
                if (message instanceof ResponsesAssistantMessage assistantMessage) {
                    List<ResponsesContent> contents = assistantMessage.getContent();
                    if (isNullOrEmpty(contents)) {
                        continue;
                    }
                    for (ResponsesContent content : contents) {
                        if (content instanceof ResponsesOutputContentText outputText) {
                            String text = outputText.getText();
                            if (!isNullOrBlank(text)) {
                                builder.append(text);
                            }
                        }
                    }
                }
            }
        }
        String text = builder.toString();
        return isNullOrBlank(text) ? null : text;
    }

    private static String extractReasoningSummary(ResponsesResponse response) {
        if (response == null || isNullOrEmpty(response.getOutput())) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (ResponsesItem item : response.getOutput()) {
            if (item instanceof ResponsesReasoningItem reasoningItem) {
                List<ResponsesReasoningItemSummaryElement> summary = reasoningItem.getSummary();
                if (isNullOrEmpty(summary)) {
                    continue;
                }
                for (ResponsesReasoningItemSummaryElement element : summary) {
                    if (element instanceof ResponsesReasoningItemSummaryElementSummaryText summaryText) {
                        String text = summaryText.getText();
                        if (isNullOrBlank(text)) {
                            continue;
                        }
                        if (!builder.isEmpty()) {
                            builder.append("\n");
                        }
                        builder.append(text);
                    }
                }
            }
        }
        String summary = builder.toString();
        return isNullOrBlank(summary) ? null : summary;
    }

    static Integer extractReasoningTokens(ResponsesResponse response) {
        if (response == null || response.getUsage() == null) {
            return null;
        }
        if (response.getUsage().getOutputTokensDetails() == null) {
            return null;
        }
        return response.getUsage().getOutputTokensDetails().getReasoningTokens();
    }

    private static List<ToolExecutionRequest> extractToolExecutionRequests(ResponsesResponse response) {
        if (response == null || isNullOrEmpty(response.getOutput())) {
            return List.of();
        }
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        for (ResponsesItem item : response.getOutput()) {
            if (item instanceof ResponsesFunctionCallItem functionCall) {
                toolExecutionRequests.add(ToolExecutionRequest.builder()
                        .id(functionCall.getCallId())
                        .name(functionCall.getName())
                        .arguments(functionCall.getArguments())
                        .build());
            }
        }
        return toolExecutionRequests;
    }

    static TokenUsage tokenUsageFrom(ResponsesResponseUsage usage) {
        if (usage == null) {
            return null;
        }
        return new TokenUsage(usage.getInputTokens(), usage.getOutputTokens(), usage.getTotalTokens());
    }

    static FinishReason finishReasonFrom(ResponsesResponse response, List<ToolExecutionRequest> toolCalls) {
        if (!isNullOrEmpty(toolCalls)) {
            return TOOL_EXECUTION;
        }
        if (response == null || response.getStatus() == null) {
            return null;
        }
        if (response.getStatus() == ResponsesResponseStatus.COMPLETED) {
            return STOP;
        }
        if (response.getStatus() == ResponsesResponseStatus.INCOMPLETE) {
            ResponsesResponseIncompleteDetails details = response.getIncompleteDetails();
            if (details != null && details.getReason() != null) {
                if (details.getReason() == ResponsesResponseIncompleteDetailsReason.MAX_OUTPUT_TOKENS) {
                    return LENGTH;
                }
                if (details.getReason() == ResponsesResponseIncompleteDetailsReason.CONTENT_FILTER) {
                    return CONTENT_FILTER;
                }
            }
        }
        return OTHER;
    }

    static CreateResponsesRequestModel toModel(String modelName) {
        return CreateResponsesRequestModel.fromString(modelName);
    }
}
