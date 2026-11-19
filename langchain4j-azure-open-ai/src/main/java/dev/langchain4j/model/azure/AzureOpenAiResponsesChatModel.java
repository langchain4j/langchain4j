package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.AZURE_OPEN_AI;
import static dev.langchain4j.model.azure.InternalAzureOpenAiResponsesHelper.ensureDeploymentName;
import static dev.langchain4j.model.azure.InternalAzureOpenAiResponsesHelper.headersSupplier;
import static dev.langchain4j.model.azure.InternalAzureOpenAiResponsesHelper.httpClientBuilder;
import static dev.langchain4j.model.azure.InternalAzureOpenAiResponsesHelper.responsesBaseUrl;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Arrays.asList;

import com.azure.core.credential.TokenCredential;
import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.azure.spi.AzureOpenAiResponsesChatModelBuilderFactory;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiPromptCacheOptions;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Represents an OpenAI language model, hosted on Azure, accessed through the
 * <a href="https://learn.microsoft.com/azure/ai-foundry/openai/how-to/responses">Responses API</a>.
 * <p>
 * As the Azure OpenAI SDK does not support the Responses API, this model uses the Azure OpenAI v1 API
 * ({@code https://{resource}.openai.azure.com/openai/v1/responses}), which does not require a service version.
 * <p>
 * Mandatory parameters for initialization are: endpoint, deploymentName and either apiKey
 * or tokenCredential (Microsoft Entra ID, requires the {@code com.azure:azure-identity} dependency).
 */
@Experimental
public class AzureOpenAiResponsesChatModel implements ChatModel {

    private final OpenAiResponsesChatModel delegate;
    private final int maxRetries;
    private final List<ChatModelListener> listeners;

    public AzureOpenAiResponsesChatModel(Builder builder) {
        ensureDeploymentName(builder.deploymentName, builder.defaultRequestParameters);
        this.delegate = builder.delegateBuilder
                .httpClientBuilder(httpClientBuilder(builder.httpClientBuilder, builder.timeout))
                .baseUrl(responsesBaseUrl(builder.endpoint))
                .customHeaders(headersSupplier(
                        builder.apiKey,
                        builder.tokenCredential,
                        builder.timeout,
                        builder.userAgentSuffix,
                        builder.customHeadersSupplier))
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.listeners = copy(builder.listeners);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return withRetryMappingExceptions(() -> delegate.doChat(chatRequest), maxRetries);
    }

    @Experimental
    @Override
    public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
        return withRetryMappingExceptionsAsync(() -> delegate.doChatAsync(chatRequest), maxRetries);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return AZURE_OPEN_AI;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    public static Builder builder() {
        for (AzureOpenAiResponsesChatModelBuilderFactory factory :
                loadFactories(AzureOpenAiResponsesChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    /**
     * Builder of {@link AzureOpenAiResponsesChatModel}.
     * <p>
     * Responses API parameters that are not exposed by this builder can be set through
     * {@link #defaultRequestParameters(ChatRequestParameters)}, using
     * {@link dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters}.
     */
    public static class Builder {

        private final OpenAiResponsesChatModel.Builder delegateBuilder = OpenAiResponsesChatModel.builder();

        private String endpoint;
        private String apiKey;
        private TokenCredential tokenCredential;
        private HttpClientBuilder httpClientBuilder;
        private String deploymentName;
        private Duration timeout;
        private Integer maxRetries;
        private String userAgentSuffix;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;

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
         * Used to authenticate to Azure OpenAI with Microsoft Entra ID (formerly Azure Active Directory) credentials.
         *
         * @param tokenCredential the credentials to authenticate with Microsoft Entra ID
         * @return builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the deployment name in Azure OpenAI. This is a mandatory parameter.
         *
         * @param deploymentName The Deployment name.
         * @return builder
         */
        public Builder deploymentName(String deploymentName) {
            this.deploymentName = deploymentName;
            delegateBuilder.modelName(deploymentName);
            return this;
        }

        public Builder temperature(Double temperature) {
            delegateBuilder.temperature(temperature);
            return this;
        }

        public Builder topP(Double topP) {
            delegateBuilder.topP(topP);
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            delegateBuilder.maxOutputTokens(maxOutputTokens);
            return this;
        }

        public Builder maxToolCalls(Integer maxToolCalls) {
            delegateBuilder.maxToolCalls(maxToolCalls);
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            delegateBuilder.parallelToolCalls(parallelToolCalls);
            return this;
        }

        public Builder previousResponseId(String previousResponseId) {
            delegateBuilder.previousResponseId(previousResponseId);
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            delegateBuilder.topLogprobs(topLogprobs);
            return this;
        }

        public Builder truncation(String truncation) {
            delegateBuilder.truncation(truncation);
            return this;
        }

        public Builder include(List<String> include) {
            delegateBuilder.include(include);
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            delegateBuilder.serviceTier(serviceTier);
            return this;
        }

        public Builder safetyIdentifier(String safetyIdentifier) {
            delegateBuilder.safetyIdentifier(safetyIdentifier);
            return this;
        }

        public Builder promptCacheKey(String promptCacheKey) {
            delegateBuilder.promptCacheKey(promptCacheKey);
            return this;
        }

        public Builder promptCacheRetention(String promptCacheRetention) {
            delegateBuilder.promptCacheRetention(promptCacheRetention);
            return this;
        }

        public Builder promptCacheOptions(OpenAiPromptCacheOptions promptCacheOptions) {
            delegateBuilder.promptCacheOptions(promptCacheOptions);
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            delegateBuilder.reasoningEffort(reasoningEffort);
            return this;
        }

        public Builder reasoningSummary(String reasoningSummary) {
            delegateBuilder.reasoningSummary(reasoningSummary);
            return this;
        }

        public Builder textVerbosity(String textVerbosity) {
            delegateBuilder.textVerbosity(textVerbosity);
            return this;
        }

        public Builder store(Boolean store) {
            delegateBuilder.store(store);
            return this;
        }

        public Builder strictTools(Boolean strictTools) {
            delegateBuilder.strictTools(strictTools);
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            delegateBuilder.strictJsonSchema(strictJsonSchema);
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            delegateBuilder.responseFormat(responseFormat);
            return this;
        }

        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            delegateBuilder.toolSpecifications(toolSpecifications);
            return this;
        }

        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public Builder serverTools(List<Map<String, Object>> serverTools) {
            delegateBuilder.serverTools(serverTools);
            return this;
        }

        public Builder toolChoice(ToolChoice toolChoice) {
            delegateBuilder.toolChoice(toolChoice);
            return this;
        }

        /**
         * Sets the connect and read timeout. Defaults to 60 seconds, unless set on the {@link HttpClientBuilder}.
         * When set, it takes precedence over the timeouts of the {@link HttpClientBuilder}.
         * It also bounds the time spent waiting for a Microsoft Entra ID token, defaulting to 60 seconds.
         *
         * @param timeout the timeout
         * @return builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries. Defaults to 2.
         *
         * @param maxRetries the maximum number of retries
         * @return builder
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            delegateBuilder.logRequests(logRequests);
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            delegateBuilder.logResponses(logResponses);
            return this;
        }

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            return logRequests(logRequestsAndResponses).logResponses(logRequestsAndResponses);
        }

        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder listeners(ChatModelListener... listeners) {
            return listeners(asList(listeners));
        }

        /**
         * Sets custom HTTP headers to be added to each HTTP request.
         *
         * @param customHeaders a map of headers
         * @return {@code this}
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers to be added to each HTTP request.
         * The supplier is called before each request, allowing dynamic header values.
         *
         * @param customHeadersSupplier a supplier that provides a map of headers
         * @return {@code this}
         */
        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets the default request parameters. Use
         * {@link dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters} to set the Responses API
         * parameters that are not exposed by this builder:
         * <pre>{@code
         * .defaultRequestParameters(OpenAiResponsesChatRequestParameters.builder()
         *         .reasoningEffort("high")
         *         .build())
         * }</pre>
         * Values set directly on this builder take precedence over the ones of these parameters.
         *
         * @param parameters the default request parameters
         * @return builder
         */
        public Builder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            delegateBuilder.defaultRequestParameters(parameters);
            return this;
        }

        public AzureOpenAiResponsesChatModel build() {
            return new AzureOpenAiResponsesChatModel(this);
        }
    }
}
