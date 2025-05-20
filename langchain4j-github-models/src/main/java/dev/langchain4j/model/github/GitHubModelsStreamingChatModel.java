package dev.langchain4j.model.github;

import com.azure.ai.inference.ChatCompletionsAsyncClient;
import com.azure.ai.inference.ModelServiceVersion;
import com.azure.ai.inference.models.ChatCompletionsOptions;
import com.azure.ai.inference.models.ChatCompletionsResponseFormat;
import com.azure.ai.inference.models.StreamingChatChoiceUpdate;
import com.azure.ai.inference.models.StreamingChatCompletionsUpdate;
import com.azure.ai.inference.models.StreamingChatResponseMessageUpdate;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.github.spi.GitHubModelsStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.GITHUB_MODELS;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.contentFilterManagement;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.createListenerRequest;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.createListenerResponse;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.setupChatCompletionsBuilder;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.toAzureAiMessages;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.toToolChoice;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.toToolDefinitions;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Represents a language model, hosted on GitHub Models, that has a chat completion interface, such as gpt-4o.
 * <p>
 * Mandatory parameters for initialization are: gitHubToken (the GitHub Token used for authentication) and modelName (the name of the model to use).
 * You can also provide your own ChatCompletionsClient and ChatCompletionsAsyncClient instance, if you need more flexibility.
 * <p>
 * The list of models, as well as the documentation and a playground to test them, can be found at https://github.com/marketplace/models
 */
public class GitHubModelsStreamingChatModel implements StreamingChatModel {

    private static final Logger logger = LoggerFactory.getLogger(GitHubModelsStreamingChatModel.class);

    private ChatCompletionsAsyncClient client;
    private final String modelName;
    private final Integer maxTokens;
    private final Double temperature;
    private final Double topP;
    private final List<String> stop;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Long seed;
    private final ChatCompletionsResponseFormat responseFormat;
    private final List<ChatModelListener> listeners;

    private GitHubModelsStreamingChatModel(ChatCompletionsAsyncClient client,
                                           String modelName,
                                           Integer maxTokens,
                                           Double temperature,
                                           Double topP,
                                           List<String> stop,
                                           Double presencePenalty,
                                           Double frequencyPenalty,
                                           Long seed,
                                           ChatCompletionsResponseFormat responseFormat,
                                           List<ChatModelListener> listeners) {

        this(modelName, maxTokens, temperature, topP, stop, presencePenalty, frequencyPenalty, seed, responseFormat, listeners);
        this.client = client;
    }

    private GitHubModelsStreamingChatModel(String endpoint,
                                           ModelServiceVersion serviceVersion,
                                           String gitHubToken,
                                           String modelName,
                                           Integer maxTokens,
                                           Double temperature,
                                           Double topP,
                                           List<String> stop,
                                           Double presencePenalty,
                                           Double frequencyPenalty,
                                           Long seed,
                                           ChatCompletionsResponseFormat responseFormat,
                                           Duration timeout,
                                           Integer maxRetries,
                                           ProxyOptions proxyOptions,
                                           boolean logRequestsAndResponses,
                                           List<ChatModelListener> listeners,
                                           String userAgentSuffix,
                                           Map<String, String> customHeaders) {

        this(modelName, maxTokens, temperature, topP, stop, presencePenalty, frequencyPenalty, seed, responseFormat, listeners);
        this.client = setupChatCompletionsBuilder(endpoint, serviceVersion, gitHubToken, timeout, maxRetries, proxyOptions, logRequestsAndResponses, userAgentSuffix, customHeaders)
                .buildAsyncClient();
    }

    private GitHubModelsStreamingChatModel(String modelName,
                                           Integer maxTokens,
                                           Double temperature,
                                           Double topP,
                                           List<String> stop,
                                           Double presencePenalty,
                                           Double frequencyPenalty,
                                           Long seed,
                                           ChatCompletionsResponseFormat responseFormat,
                                           List<ChatModelListener> listeners) {

        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.stop = copyIfNotNull(stop);
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.seed = seed;
        this.responseFormat = responseFormat;
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = request.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                handler.onPartialResponse(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(response.content())
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(response.tokenUsage())
                                .finishReason(response.finishReason())
                                .build())
                        .build();
                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            generate(request.messages(), legacyHandler);
        } else {
            if (parameters.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(
                            "%s.%s is currently supported only when there is a single tool".formatted(
                                    ToolChoice.class.getSimpleName(), REQUIRED.name()));
                }
                generate(request.messages(), toolSpecifications.get(0), legacyHandler);
            } else {
                generate(request.messages(), toolSpecifications, legacyHandler);
            }
        }
    }

    private void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, null, handler);
    }

    private void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    private void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, toolSpecification, handler);
    }

    private void generate(List<ChatMessage> messages,
                          List<ToolSpecification> toolSpecifications,
                          ToolSpecification toolThatMustBeExecuted,
                          StreamingResponseHandler<AiMessage> handler
    ) {
        ChatCompletionsOptions options = new ChatCompletionsOptions(toAzureAiMessages(messages))
                .setModel(modelName)
                .setMaxTokens(maxTokens)
                .setTemperature(temperature)
                .setTopP(topP)
                .setStop(stop)
                .setPresencePenalty(presencePenalty)
                .setFrequencyPenalty(frequencyPenalty)
                .setSeed(seed)
                .setResponseFormat(responseFormat);

        if (toolThatMustBeExecuted != null) {
            options.setTools(toToolDefinitions(singletonList(toolThatMustBeExecuted)));
            options.setToolChoice(toToolChoice(toolThatMustBeExecuted));
        }
        if (!isNullOrEmpty(toolSpecifications)) {
            options.setTools(toToolDefinitions(toolSpecifications));
        }

        GitHubModelsStreamingResponseBuilder responseBuilder = new GitHubModelsStreamingResponseBuilder();

        ChatRequest listenerRequest = createListenerRequest(options, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext =
                new ChatModelRequestContext(listenerRequest, provider(), attributes);

        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                logger.warn("Exception while calling model listener", e);
            }
        });

        asyncCall(handler, options, responseBuilder, requestContext);
    }

    private void handleResponseException(Throwable throwable, StreamingResponseHandler<AiMessage> handler) {
        if (throwable instanceof HttpResponseException) {
            HttpResponseException httpResponseException = (HttpResponseException) throwable;
            logger.info("Error generating response, {}", httpResponseException.getValue());
            FinishReason exceptionFinishReason = contentFilterManagement(httpResponseException, "content_filter");
            if (exceptionFinishReason == FinishReason.CONTENT_FILTER) {
                Response<AiMessage> response = Response.from(
                        aiMessage(httpResponseException.getMessage()),
                        null,
                        exceptionFinishReason
                );
                handler.onComplete(response);
            } else {
                handler.onError(throwable);
            }
        } else {
            handler.onError(throwable);
        }
    }

    private void asyncCall(StreamingResponseHandler<AiMessage> handler, ChatCompletionsOptions options, GitHubModelsStreamingResponseBuilder responseBuilder, ChatModelRequestContext requestContext) {
        Flux<StreamingChatCompletionsUpdate> chatCompletionsStream = client.completeStream(options);

        AtomicReference<String> responseId = new AtomicReference<>();
        AtomicReference<String> responseModel = new AtomicReference<>();

        chatCompletionsStream.subscribe(chatCompletion -> {
                    responseBuilder.append(chatCompletion);
                    handle(chatCompletion, handler);

                    if (isNotNullOrBlank(chatCompletion.getId())) {
                        responseId.set(chatCompletion.getId());
                    }
                    if (!isNullOrBlank(chatCompletion.getModel())) {
                        responseModel.set(chatCompletion.getModel());
                    }
                },
                throwable -> {
                    ChatModelErrorContext errorContext = new ChatModelErrorContext(
                            throwable,
                            requestContext.chatRequest(),
                            provider(),
                            requestContext.attributes()
                    );
                    listeners.forEach(listener -> {
                        try {
                            listener.onError(errorContext);
                        } catch (Exception e2) {
                            logger.warn("Exception while calling model listener", e2);
                        }
                    });
                    handleResponseException(throwable, handler);
                },
                () -> {
                    Response<AiMessage> response = responseBuilder.build();
                    ChatResponse listenerResponse = createListenerResponse(
                            responseId.get(),
                            options.getModel(),
                            response
                    );
                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                            listenerResponse,
                            requestContext.chatRequest(),
                            provider(),
                            requestContext.attributes()
                    );
                    listeners.forEach(listener -> {
                        try {
                            listener.onResponse(responseContext);
                        } catch (Exception e) {
                            logger.warn("Exception while calling model listener", e);
                        }
                    });
                    handler.onComplete(response);
                });
    }


    private static void handle(StreamingChatCompletionsUpdate chatCompletions,
                               StreamingResponseHandler<AiMessage> handler) {

        List<StreamingChatChoiceUpdate> choices = chatCompletions.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        StreamingChatResponseMessageUpdate message = choices.get(0).getDelta();
        if (message != null && message.getContent() != null) {
            handler.onNext(message.getContent());
        }
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return GITHUB_MODELS;
    }

    public static Builder builder() {
        for (GitHubModelsStreamingChatModelBuilderFactory factory : loadFactories(GitHubModelsStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private ModelServiceVersion serviceVersion;
        private String gitHubToken;
        private String modelName;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private List<String> stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        private Long seed;
        private ChatCompletionsResponseFormat responseFormat;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private ChatCompletionsAsyncClient client;
        private String userAgentSuffix;
        private List<ChatModelListener> listeners;
        private Map<String, String> customHeaders;

        /**
         * Sets the GitHub Models endpoint. The default endpoint will be used if this isn't set.
         *
         * @param endpoint The GitHub Models endpoint in the format: https://models.inference.ai.azure.com
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API service version. If left blank, the latest service version will be used.
         *
         * @param serviceVersion The Azure OpenAI API service version in the format: 2023-05-15
         * @return builder
         */
        public Builder serviceVersion(ModelServiceVersion serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the GitHub token to access GitHub Models.
         *
         * @param gitHubToken The GitHub token.
         * @return builder
         */
        public Builder gitHubToken(String gitHubToken) {
            this.gitHubToken = gitHubToken;
            return this;
        }

        /**
         * Sets the model name in Azure OpenAI. This is a mandatory parameter.
         *
         * @param modelName The Model name.
         * @return builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(GitHubModelsChatModelName modelName) {
            this.modelName = modelName.toString();
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

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        public Builder responseFormat(ChatCompletionsResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
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

        public Builder proxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder chatCompletionsAsyncClient(ChatCompletionsAsyncClient client) {
            this.client = client;
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

        public GitHubModelsStreamingChatModel build() {
            if (client != null) {
                return new GitHubModelsStreamingChatModel(
                        client,
                        modelName,
                        maxTokens,
                        temperature,
                        topP,
                        stop,
                        presencePenalty,
                        frequencyPenalty,
                        seed,
                        responseFormat,
                        listeners
                );
            } else {
                return new GitHubModelsStreamingChatModel(
                        endpoint,
                        serviceVersion,
                        gitHubToken,
                        modelName,
                        maxTokens,
                        temperature,
                        topP,
                        stop,
                        presencePenalty,
                        frequencyPenalty,
                        seed,
                        responseFormat,
                        timeout,
                        maxRetries,
                        proxyOptions,
                        logRequestsAndResponses,
                        listeners,
                        userAgentSuffix,
                        customHeaders
                );
            }
        }
    }
}
