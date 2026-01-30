package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ModelProvider.AZURE_OPEN_AI;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.validate;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import com.azure.ai.openai.responses.ResponsesAsyncClient;
import com.azure.ai.openai.responses.models.CreateResponsesRequest;
import com.azure.ai.openai.responses.models.ResponsesReasoningConfiguration;
import com.azure.ai.openai.responses.models.ResponsesReasoningConfigurationEffort;
import com.azure.ai.openai.responses.models.ResponsesReasoningConfigurationGenerateSummary;
import com.azure.ai.openai.responses.models.ResponsesResponse;
import com.azure.ai.openai.responses.models.ResponsesStreamEvent;
import com.azure.ai.openai.responses.models.ResponsesStreamEventCompleted;
import com.azure.ai.openai.responses.models.ResponsesStreamEventCreated;
import com.azure.ai.openai.responses.models.ResponsesStreamEventError;
import com.azure.ai.openai.responses.models.ResponsesStreamEventFailed;
import com.azure.ai.openai.responses.models.ResponsesStreamEventIncomplete;
import com.azure.ai.openai.responses.models.ResponsesStreamEventOutputTextDelta;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.core.util.BinaryData;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.azure.spi.AzureOpenAiResponsesStreamingChatModelBuilderFactory;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Represents an OpenAI model, hosted on Azure, that uses the Responses API.
 * The model's response is streamed token by token and should be handled with {@link StreamingChatResponseHandler}.
 * Reasoning summaries are only available when the service emits a reasoning output item;
 * otherwise {@link dev.langchain4j.data.message.AiMessage#thinking()} will be {@code null}.
 */
public class AzureOpenAiResponsesStreamingChatModel implements StreamingChatModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureOpenAiResponsesStreamingChatModel.class);

    private final ResponsesAsyncClient client;
    private final ChatRequestParameters defaultRequestParameters;

    private final String user;
    private final boolean strictJsonSchema;
    private final ResponsesReasoningConfigurationEffort reasoningEffort;
    private final String reasoningSummary;
    private final boolean logRequestsAndResponses;

    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;

    public AzureOpenAiResponsesStreamingChatModel(Builder builder) {
        if (builder.responsesAsyncClient == null) {
            if (builder.tokenCredential != null) {
                this.client = InternalAzureOpenAiResponsesHelper.setupAsyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.tokenCredential,
                        builder.timeout,
                        builder.maxRetries,
                        builder.retryOptions,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            } else if (builder.keyCredential != null) {
                this.client = InternalAzureOpenAiResponsesHelper.setupAsyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.keyCredential,
                        builder.timeout,
                        builder.maxRetries,
                        builder.retryOptions,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            } else {
                this.client = InternalAzureOpenAiResponsesHelper.setupAsyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.apiKey,
                        builder.timeout,
                        builder.maxRetries,
                        builder.retryOptions,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            }
        } else {
            this.client = ensureNotNull(builder.responsesAsyncClient, "responsesAsyncClient");
        }

        ChatRequestParameters parameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            parameters = builder.defaultRequestParameters;
        } else {
            parameters = DefaultChatRequestParameters.EMPTY;
        }

        this.defaultRequestParameters = ChatRequestParameters.builder()
                .modelName(getOrDefault(builder.deploymentName, parameters.modelName()))
                .temperature(getOrDefault(builder.temperature, parameters.temperature()))
                .topP(getOrDefault(builder.topP, parameters.topP()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, parameters.maxOutputTokens()))
                .toolSpecifications(parameters.toolSpecifications())
                .toolChoice(parameters.toolChoice())
                .responseFormat(getOrDefault(builder.responseFormat, parameters.responseFormat()))
                .build();

        this.user = builder.user;
        this.strictJsonSchema = getOrDefault(builder.strictJsonSchema, false);
        this.reasoningEffort = builder.reasoningEffort;
        this.reasoningSummary = builder.reasoningSummary;
        this.logRequestsAndResponses = builder.logRequestsAndResponses;

        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = request.parameters();
        validate(parameters);

        List<com.azure.ai.openai.responses.models.ResponsesMessage> messages =
                InternalAzureOpenAiResponsesHelper.toResponsesMessages(request.messages());

        ResponseFormat responseFormat = parameters.responseFormat();
        com.azure.ai.openai.responses.models.ResponseTextOptions textOptions = null;
        if (responseFormat != null) {
            textOptions = InternalAzureOpenAiResponsesHelper.toResponseTextOptions(responseFormat, strictJsonSchema);
        }

        List<com.azure.ai.openai.responses.models.ResponsesTool> tools =
                parameters.toolSpecifications().isEmpty()
                        ? List.of()
                        : InternalAzureOpenAiResponsesHelper.toResponsesTools(
                                parameters.toolSpecifications(), strictJsonSchema);

        Flux<ResponsesStreamEvent> stream;
        if (reasoningSummary != null) {
            BinaryData requestBody = InternalAzureOpenAiResponsesHelper.buildRequestBody(
                    parameters.modelName(),
                    messages,
                    parameters.temperature(),
                    parameters.topP(),
                    parameters.maxOutputTokens(),
                    user,
                    textOptions,
                    reasoningEffort,
                    reasoningSummary,
                    tools,
                    parameters.toolChoice(),
                    true);
            stream = InternalAzureOpenAiResponsesHelper.createResponseStreamWithRawRequest(client, requestBody);
        } else {
            CreateResponsesRequest createRequest = new CreateResponsesRequest(
                    InternalAzureOpenAiResponsesHelper.toModel(parameters.modelName()), messages);
            createRequest.setTemperature(parameters.temperature());
            createRequest.setTopP(parameters.topP());
            createRequest.setMaxOutputTokens(parameters.maxOutputTokens());
            createRequest.setUser(user);
            if (textOptions != null) {
                createRequest.setText(textOptions);
            }
            if (reasoningEffort != null) {
                ResponsesReasoningConfiguration reasoning = new ResponsesReasoningConfiguration(reasoningEffort);
                createRequest.setReasoning(reasoning);
            }
            if (!tools.isEmpty()) {
                createRequest.setTools(tools);
            }
            if (parameters.toolChoice() != null) {
                createRequest.setToolChoice(
                        InternalAzureOpenAiResponsesHelper.toToolChoiceBinaryData(parameters.toolChoice()));
            }
            stream = client.createResponseStream(createRequest);
        }

        AtomicReference<ResponsesResponse> completedResponse = new AtomicReference<>();
        AtomicReference<String> responseId = new AtomicReference<>();
        AtomicReference<String> responseModelName = new AtomicReference<>();
        AtomicReference<StreamingHandle> streamingHandle = new AtomicReference<>();
        AtomicBoolean errorReported = new AtomicBoolean(false);
        StringBuilder textBuilder = new StringBuilder();

        Disposable disposable = stream.subscribe(
                event -> handleEvent(
                        event,
                        handler,
                        streamingHandle.get(),
                        textBuilder,
                        completedResponse,
                        responseId,
                        responseModelName,
                        errorReported),
                error -> {
                    RuntimeException mappedError = AzureOpenAiExceptionMapper.INSTANCE.mapException(error);
                    errorReported.set(true);
                    withLoggingExceptions(() -> handler.onError(mappedError));
                },
                () -> {
                    if (errorReported.get()) {
                        return;
                    }
                    ResponsesResponse response = completedResponse.get();
                    Response<dev.langchain4j.data.message.AiMessage> parsed =
                            InternalAzureOpenAiResponsesHelper.toResponse(response, textBuilder.toString());
                    if (logRequestsAndResponses) {
                        Integer reasoningTokens = InternalAzureOpenAiResponsesHelper.extractReasoningTokens(response);
                        if (parsed.content().thinking() == null && reasoningTokens != null && reasoningTokens > 0) {
                            LOGGER.info(
                                    "Reasoning tokens were reported ({}), but no reasoning summary text was returned. "
                                            + "This can happen depending on the model/service.",
                                    reasoningTokens);
                        }
                    }

                    List<ToolExecutionRequest> toolCalls = parsed.content().toolExecutionRequests();
                    for (int i = 0; i < toolCalls.size(); i++) {
                        onCompleteToolCall(handler, new CompleteToolCall(i, toolCalls.get(i)));
                    }

                    ChatResponse chatResponse = ChatResponse.builder()
                            .aiMessage(parsed.content())
                            .metadata(ChatResponseMetadata.builder()
                                    .id(responseId.get())
                                    .modelName(responseModelName.get())
                                    .tokenUsage(parsed.tokenUsage())
                                    .finishReason(parsed.finishReason())
                                    .build())
                            .build();

                    onCompleteResponse(handler, chatResponse);
                });

        streamingHandle.set(new AzureOpenAiStreamingHandle(disposable));
    }

    private static void handleEvent(
            ResponsesStreamEvent event,
            StreamingChatResponseHandler handler,
            StreamingHandle streamingHandle,
            StringBuilder textBuilder,
            AtomicReference<ResponsesResponse> completedResponse,
            AtomicReference<String> responseId,
            AtomicReference<String> responseModelName,
            AtomicBoolean errorReported) {
        if (event == null) {
            return;
        }

        if (event instanceof ResponsesStreamEventOutputTextDelta textDelta) {
            String delta = textDelta.getDelta();
            if (!isNullOrBlank(delta)) {
                textBuilder.append(delta);
                onPartialResponse(handler, delta, streamingHandle);
            }
            return;
        }

        if (event instanceof ResponsesStreamEventCreated created) {
            ResponsesResponse response = created.getResponse();
            if (response != null) {
                if (!isNullOrBlank(response.getId())) {
                    responseId.set(response.getId());
                }
                if (!isNullOrBlank(response.getModel())) {
                    responseModelName.set(response.getModel());
                }
            }
            return;
        }

        if (event instanceof ResponsesStreamEventCompleted completed) {
            ResponsesResponse response = completed.getResponse();
            if (response != null) {
                completedResponse.set(response);
                if (!isNullOrBlank(response.getId())) {
                    responseId.set(response.getId());
                }
                if (!isNullOrBlank(response.getModel())) {
                    responseModelName.set(response.getModel());
                }
            }
            return;
        }

        if (event instanceof ResponsesStreamEventIncomplete incomplete) {
            ResponsesResponse response = incomplete.getResponse();
            if (response != null) {
                completedResponse.set(response);
                if (!isNullOrBlank(response.getId())) {
                    responseId.set(response.getId());
                }
                if (!isNullOrBlank(response.getModel())) {
                    responseModelName.set(response.getModel());
                }
            }
            return;
        }

        if (event instanceof ResponsesStreamEventFailed failed) {
            errorReported.set(true);
            ResponsesResponse response = failed.getResponse();
            String message = response != null
                    ? response.getError() != null ? response.getError().getMessage() : "Responses stream failed"
                    : "Responses stream failed";
            withLoggingExceptions(() -> handler.onError(new RuntimeException(message)));
            return;
        }

        if (event instanceof ResponsesStreamEventError errorEvent) {
            errorReported.set(true);
            String message = errorEvent.getMessage() != null
                    ? "Responses stream error: " + errorEvent.getMessage()
                    : "Responses stream error";
            withLoggingExceptions(() -> handler.onError(new RuntimeException(message)));
        }
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return AZURE_OPEN_AI;
    }

    public static Builder builder() {
        for (AzureOpenAiResponsesStreamingChatModelBuilderFactory factory :
                loadFactories(AzureOpenAiResponsesStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private ChatRequestParameters defaultRequestParameters;

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private KeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private HttpClientProvider httpClientProvider;
        private String deploymentName;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private String user;
        private ResponseFormat responseFormat;
        private Boolean strictJsonSchema;
        private Duration timeout;
        private Integer maxRetries;
        private RetryOptions retryOptions;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private ResponsesAsyncClient responsesAsyncClient;
        private String userAgentSuffix;
        private List<ChatModelListener> listeners;
        private Map<String, String> customHeaders;
        private Set<Capability> supportedCapabilities;
        private ResponsesReasoningConfigurationEffort reasoningEffort;
        private String reasoningSummary;

        public Builder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        /**
         * Sets the Azure OpenAI endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure OpenAI endpoint in the format: https://{resource}.openai.azure.com/
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API service version. This is optional; if not set, the latest is used.
         *
         * @param serviceVersion The Azure OpenAI API service version in the format: 2024-10-21
         * @return builder
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the Azure OpenAI API key.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Used to authenticate with the OpenAI service, instead of Azure OpenAI.
         * This automatically sets the endpoint to https://api.openai.com/v1.
         *
         * @param nonAzureApiKey The non-Azure OpenAI API key
         * @return builder
         */
        public Builder nonAzureApiKey(String nonAzureApiKey) {
            this.keyCredential = new KeyCredential(nonAzureApiKey);
            this.endpoint = "https://api.openai.com/v1";
            return this;
        }

        public Builder keyCredential(KeyCredential keyCredential) {
            this.keyCredential = keyCredential;
            return this;
        }

        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        public Builder httpClientProvider(HttpClientProvider httpClientProvider) {
            this.httpClientProvider = httpClientProvider;
            return this;
        }

        /**
         * Sets the Azure OpenAI deployment name (model). This is a mandatory parameter.
         *
         * @param deploymentName The Azure OpenAI deployment name.
         * @return builder
         */
        public Builder deploymentName(String deploymentName) {
            this.deploymentName = deploymentName;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public Builder reasoningEffort(ResponsesReasoningConfigurationEffort reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder reasoningSummary(ResponsesReasoningConfigurationGenerateSummary reasoningSummary) {
            this.reasoningSummary = reasoningSummary != null ? reasoningSummary.toString() : null;
            return this;
        }

        /**
         * Sets the reasoning summary mode. Accepts values like "auto", "concise", or "detailed".
         */
        public Builder reasoningSummary(String reasoningSummary) {
            this.reasoningSummary = reasoningSummary;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryOptions(RetryOptions retryOptions) {
            this.retryOptions = retryOptions;
            return this;
        }

        public Builder proxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder responsesAsyncClient(ResponsesAsyncClient responsesAsyncClient) {
            this.responsesAsyncClient = responsesAsyncClient;
            return this;
        }

        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public Builder supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(List.of(supportedCapabilities)));
        }

        public AzureOpenAiResponsesStreamingChatModel build() {
            return new AzureOpenAiResponsesStreamingChatModel(this);
        }
    }
}
