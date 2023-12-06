package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.Completions;
import com.azure.ai.openai.models.CompletionsOptions;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientProvider;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.util.HttpClientOptions;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.Collections;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.azure.AzureOpenAiModelName.GPT_3_5_TURBO_INSTRUCT;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.*;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_DAVINCI_003;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI language model, hosted on Azure, such as gpt-3.5-turbo-instruct.
 * However, it's recommended to use {@link AzureOpenAiChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 * <p>
 * Mandatory parameters for initialization are: endpoint, serviceVersion, apiKey and deploymentName.
 * <p>
 * There are two primary authentication methods to access Azure OpenAI:
 * <p>
 * 1. API Key Authentication: For this type of authentication, HTTP requests must include the
 * API Key in the "api-key" HTTP header as follows: `api-key: OPENAI_API_KEY`Y`
 * <p>
 * 2. Azure Active Directory Authentication: For this type of authentication, HTTP requests must include the
 * authentication/access token in the "Authorization" HTTP header.
 * <p>
 * <a href="https://learn.microsoft.com/en-us/azure/ai-services/openai/reference">More information</a>
 * <p>
 * Please note, that currently, only API Key authentication is supported by this class,
 * second authentication option will be supported later.
 */
public class AzureOpenAiLanguageModel implements LanguageModel, TokenCountEstimator {

    private final OpenAIClient client;
    private final String deploymentName;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Integer maxRetries;
    private final Tokenizer tokenizer;

    public AzureOpenAiLanguageModel(String endpoint,
                                    String serviceVersion,
                                    String apiKey,
                                    String deploymentName,
                                    String modelName,
                                    Tokenizer tokenizer,
                                    Double temperature,
                                    Double topP,
                                    Integer maxTokens,
                                    Double presencePenalty,
                                    Double frequencyPenalty,
                                    Duration timeout,
                                    Integer maxRetries,
                                    ProxyOptions proxyOptions,
                                    Boolean logRequests) {

        timeout = getOrDefault(timeout, ofSeconds(60));

        HttpClientOptions clientOptions = new HttpClientOptions();
        clientOptions.setConnectTimeout(timeout);
        clientOptions.setResponseTimeout(timeout);
        clientOptions.setReadTimeout(timeout);
        clientOptions.setWriteTimeout(timeout);
        clientOptions.setProxyOptions(proxyOptions);

        HttpClient httpClient = new NettyAsyncHttpClientProvider().createInstance(clientOptions);

        HttpLogOptions httpLogOptions = new HttpLogOptions();
        if (logRequests != null) {
            httpLogOptions.setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS);
        }

        this.client = new OpenAIClientBuilder()
                .endpoint(ensureNotBlank(endpoint, "endpoint"))
                .credential(new AzureKeyCredential(apiKey))
                .serviceVersion(getOpenAIServiceVersion(serviceVersion))
                .httpClient(httpClient)
                .httpLogOptions(httpLogOptions)
                .buildClient();

        this.deploymentName = getOrDefault(deploymentName, "gpt-35-turbo-instruct");
        this.modelName = getOrDefault(modelName, GPT_3_5_TURBO_INSTRUCT);
        this.tokenizer = getOrDefault(tokenizer, new OpenAiTokenizer(this.modelName));
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<String> generate(String prompt) {

        CompletionsOptions options = new CompletionsOptions(Collections.singletonList(prompt))
                .setModel(deploymentName)
                .setTemperature(temperature)
                .setTopP(topP)
                .setMaxTokens(maxTokens)
                .setPresencePenalty(presencePenalty)
                .setFrequencyPenalty(frequencyPenalty);

        Completions completions = withRetry(() -> client.getCompletions(deploymentName, options), maxRetries);

        return Response.from(
                completions.getChoices().get(0).getText(),
                tokenUsageFrom(completions.getUsage()),
                finishReasonFrom(completions.getChoices().get(0).getFinishReason())
        );
    }

    @Override
    public int estimateTokenCount(String prompt) {
        return tokenizer.estimateTokenCountInText(prompt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private String deploymentName;
        private String modelName;
        private Tokenizer tokenizer;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private Boolean logRequests;

        /**
         * Sets the Azure OpenAI base URL. This is a mandatory parameter.
         *
         * @param endpoint The Azure OpenAI base URL in the format: https://{resource}.openai.azure.com/
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API version. This is a mandatory parameter.
         *
         * @param serviceVersion The Azure OpenAI api version in the format: 2023-05-15
         * @return builder
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the Azure OpenAI API key. This is a mandatory parameter.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
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
            return this;
        }

        /**
         * Sets the model name in Azure OpenAI. This is a mandatory parameter.
         *
         * @param modelName The model name.
         * @return builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
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

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder ProxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public AzureOpenAiLanguageModel build() {
            return new AzureOpenAiLanguageModel(
                    endpoint,
                    serviceVersion,
                    apiKey,
                    deploymentName,
                    modelName,
                    tokenizer,
                    temperature,
                    topP,
                    maxTokens,
                    presencePenalty,
                    frequencyPenalty,
                    timeout,
                    maxRetries,
                    proxyOptions,
                    logRequests
            );
        }
    }
}
