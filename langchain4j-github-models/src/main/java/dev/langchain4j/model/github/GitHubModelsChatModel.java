package dev.langchain4j.model.github;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ModelServiceVersion;
import com.azure.ai.inference.models.ChatCompletions;
import com.azure.ai.inference.models.ChatCompletionsOptions;
import com.azure.ai.inference.models.ChatCompletionsResponseFormat;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.github.spi.GitHubModelsChatModelBuilderFactory;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.GITHUB_MODELS;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Represents a language model, hosted on GitHub Models, that has a chat completion interface, such as gpt-4o.
 * <p>
 * Mandatory parameters for initialization are: gitHubToken (the GitHub Token used for authentication) and modelName (the name of the model to use).
 * You can also provide your own ChatCompletionsClient instance, if you need more flexibility.
 * <p>
 * The list of models, as well as the documentation and a playground to test them, can be found at https://github.com/marketplace/models
 */
public class GitHubModelsChatModel implements ChatModel {

    private static final Logger logger = LoggerFactory.getLogger(GitHubModelsChatModel.class);

    private ChatCompletionsClient client;
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

    private GitHubModelsChatModel(ChatCompletionsClient client,
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

    private GitHubModelsChatModel(String endpoint,
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
        this.client = setupChatCompletionsBuilder(endpoint, serviceVersion, gitHubToken, timeout, maxRetries, proxyOptions, logRequestsAndResponses, userAgentSuffix, customHeaders).buildClient();
    }

    private GitHubModelsChatModel(String modelName,
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
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        Response<AiMessage> response;
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            response = generate(chatRequest.messages());
        } else {
            if (parameters.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(
                            String.format("%s.%s is currently supported only when there is a single tool",
                                    ToolChoice.class.getSimpleName(), REQUIRED.name()));
                }
                response = generate(chatRequest.messages(), toolSpecifications.get(0));
            } else {
                response = generate(chatRequest.messages(), toolSpecifications);
            }
        }

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, null, null);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications, null);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, singletonList(toolSpecification), toolSpecification);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolSpecification toolThatMustBeExecuted
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

        try {
            ChatCompletions chatCompletions = client.complete(options);
            Response<AiMessage> response = Response.from(
                    aiMessageFrom(chatCompletions.getChoices().get(0).getMessage()),
                    tokenUsageFrom(chatCompletions.getUsage()),
                    finishReasonFrom(chatCompletions.getChoices().get(0).getFinishReason())
            );

            ChatResponse listenerResponse = createListenerResponse(
                    chatCompletions.getId(),
                    options.getModel(),
                    response
            );
            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                    listenerResponse,
                    listenerRequest,
                    provider(),
                    attributes
            );
            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    logger.warn("Exception while calling model listener", e);
                }
            });

            return response;
        } catch (HttpResponseException httpResponseException) {
            logger.info("Error generating response, {}", httpResponseException.getValue());

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    httpResponseException,
                    listenerRequest,
                    provider(),
                    attributes
            );

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    logger.warn("Exception while calling model listener", e2);
                }
            });

            FinishReason exceptionFinishReason = contentFilterManagement(httpResponseException, "content_filter");
            if (exceptionFinishReason != FinishReason.CONTENT_FILTER) {
                throw httpResponseException;
            }

            return Response.from(
                    aiMessage(httpResponseException.getMessage()),
                    null,
                    exceptionFinishReason
            );
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
        for (GitHubModelsChatModelBuilderFactory factory : loadFactories(GitHubModelsChatModelBuilderFactory.class)) {
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
        private Long seed;
        private ChatCompletionsResponseFormat responseFormat;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private ChatCompletionsClient chatCompletionsClient;
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
         * Sets the model name in Azure AI Inference API. This is a mandatory parameter.
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

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        /**
         * Sets the Azure AI Inference API client. This is an optional parameter, if you need more flexibility than the common parameters.
         *
         * @param chatCompletionsClient The Azure AI Inference API client.
         * @return builder
         */
        public Builder chatCompletionsClient(ChatCompletionsClient chatCompletionsClient) {
            this.chatCompletionsClient = chatCompletionsClient;
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

        public GitHubModelsChatModel build() {
            if (chatCompletionsClient == null) {
                return new GitHubModelsChatModel(
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

            } else {
                return new GitHubModelsChatModel(
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
            }
        }
    }
}
