package dev.langchain4j.model.github;

import com.azure.ai.inference.ChatCompletionsAsyncClient;
import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.models.*;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
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
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.*;
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
public class GitHubModelsStreamingChatModel implements StreamingChatLanguageModel {

    private static final Logger logger = LoggerFactory.getLogger(GitHubModelsStreamingChatModel.class);

    private ChatCompletionsClient client;
    private ChatCompletionsAsyncClient asyncClient;
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

    public GitHubModelsStreamingChatModel(ChatCompletionsClient client,
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

    public GitHubModelsStreamingChatModel(ChatCompletionsAsyncClient asyncClient,
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
        this.asyncClient = asyncClient;
    }

    public GitHubModelsStreamingChatModel(String endpoint,
                                          String serviceVersion,
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
                                          boolean useAsyncClient,
                                          List<ChatModelListener> listeners,
                                          String userAgentSuffix,
                                          Map<String, String> customHeaders) {

        this(modelName, maxTokens, temperature, topP, stop, presencePenalty, frequencyPenalty, seed, responseFormat, listeners);
        if (useAsyncClient) {
            this.asyncClient = setupChatCompletionsBuilder(endpoint, serviceVersion, gitHubToken, timeout, maxRetries, proxyOptions, logRequestsAndResponses, userAgentSuffix, customHeaders)
                    .buildAsyncClient();
        } else {
            this.client = setupChatCompletionsBuilder(endpoint, serviceVersion, gitHubToken, timeout, maxRetries, proxyOptions, logRequestsAndResponses, userAgentSuffix, customHeaders)
                    .buildClient();
        }
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

        this.modelName = getOrDefault(modelName, DEFAULT_CHAT_MODEL_NAME);
        this.maxTokens = maxTokens;
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.stop = stop;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.seed = seed;
        this.responseFormat = responseFormat;
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
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

        ChatModelRequest modelListenerRequest = createModelListenerRequest(options, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);

        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                logger.warn("Exception while calling model listener", e);
            }
        });

        // Sync version
        if (client != null) {
            syncCall(toolThatMustBeExecuted, handler, options, responseBuilder, requestContext);
        } else if (asyncClient != null) {
            asyncCall(toolThatMustBeExecuted, handler, options, responseBuilder, requestContext);
        }
    }

    private void handleResponseException(Throwable throwable, StreamingResponseHandler<AiMessage> handler) {
        if (throwable instanceof HttpResponseException) {
            HttpResponseException httpResponseException = (HttpResponseException) throwable;
            logger.info("Error generating response, {}", httpResponseException.getValue());
            FinishReason exceptionFinishReason = contentFilterManagement(httpResponseException, "content_filter");
            Response<AiMessage> response =  Response.from(
                    aiMessage(httpResponseException.getMessage()),
                    null,
                    exceptionFinishReason
            );
            handler.onComplete(response);
        } else {
            handler.onError(throwable);
        }
    }

    private void asyncCall(ToolSpecification toolThatMustBeExecuted, StreamingResponseHandler<AiMessage> handler, ChatCompletionsOptions options, GitHubModelsStreamingResponseBuilder responseBuilder, ChatModelRequestContext requestContext) {
        Flux<StreamingChatCompletionsUpdate> chatCompletionsStream = asyncClient.completeStream(options);

        AtomicReference<String> responseId = new AtomicReference<>();
        chatCompletionsStream.subscribe(chatCompletion -> {
                    responseBuilder.append(chatCompletion);
                    handle(chatCompletion, handler);

                    if (isNotNullOrBlank(chatCompletion.getId())) {
                        responseId.set(chatCompletion.getId());
                    }
                },
                throwable -> {
                    ChatModelErrorContext errorContext = new ChatModelErrorContext(
                        throwable,
                        requestContext.request(),
                        null,
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
                    ChatModelResponse modelListenerResponse = createModelListenerResponse(
                        responseId.get(),
                        options.getModel(),
                        response
                    );
                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                        modelListenerResponse,
                        requestContext.request(),
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

    private void syncCall(ToolSpecification toolThatMustBeExecuted, StreamingResponseHandler<AiMessage> handler, ChatCompletionsOptions options, GitHubModelsStreamingResponseBuilder responseBuilder, ChatModelRequestContext requestContext) {
        try {
            AtomicReference<String> responseId = new AtomicReference<>();

            client.completeStream(options)
                    .stream()
                    .forEach(chatCompletions -> {
                        responseBuilder.append(chatCompletions);
                        handle(chatCompletions, handler);

                        if (isNotNullOrBlank(chatCompletions.getId())) {
                            responseId.set(chatCompletions.getId());
                        }
                    });
            Response<AiMessage> response = responseBuilder.build();
            ChatModelResponse modelListenerResponse = createModelListenerResponse(
                responseId.get(),
                options.getModel(),
                response
            );
            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                modelListenerResponse,
                requestContext.request(),
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
        } catch (Exception exception) {
            handleResponseException(exception, handler);

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                exception,
                requestContext.request(),
                null,
                requestContext.attributes()
            );

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    logger.warn("Exception while calling model listener", e2);
                }
            });
        }
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

    public static Builder builder() {
        for (GitHubModelsStreamingChatModelBuilderFactory factory : loadFactories(GitHubModelsStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String gitHubToken;
        private String modelName;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private List<String> stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        Long seed;
        ChatCompletionsResponseFormat responseFormat;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private boolean useAsyncClient = true;
        private ChatCompletionsClient chatCompletionsClient;
        private ChatCompletionsAsyncClient chatCompletionsAsyncClient;
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
         * Sets the Azure OpenAI API service version. This is a mandatory parameter.
         *
         * @param serviceVersion The Azure OpenAI API service version in the format: 2023-05-15
         * @return builder
         */
        public Builder serviceVersion(String serviceVersion) {
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

        public Builder useAsyncClient(boolean useAsyncClient) {
            this.useAsyncClient = useAsyncClient;
            return this;
        }

        public Builder chatCompletionsClient(ChatCompletionsClient chatCompletionsClient) {
            this.chatCompletionsClient = chatCompletionsClient;
            this.useAsyncClient = false;
            return this;
        }

        public Builder chatCompletionsAsyncClient(ChatCompletionsAsyncClient chatCompletionsAsyncClient) {
            this.chatCompletionsAsyncClient = chatCompletionsAsyncClient;
            this.useAsyncClient = true;
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
            if (chatCompletionsClient != null) {
                return new GitHubModelsStreamingChatModel(
                        chatCompletionsClient,
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
            } else if (chatCompletionsAsyncClient != null) {
                return new GitHubModelsStreamingChatModel(
                        chatCompletionsAsyncClient,
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
                        useAsyncClient,
                        listeners,
                        userAgentSuffix,
                        customHeaders
                );
            }
        }
    }
}
