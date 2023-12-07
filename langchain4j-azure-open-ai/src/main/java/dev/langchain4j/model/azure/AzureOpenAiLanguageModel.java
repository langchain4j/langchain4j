package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.Completions;
import com.azure.ai.openai.models.CompletionsOptions;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.Collections;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.azure.AzureOpenAiModelName.GPT_3_5_TURBO_INSTRUCT;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.*;

/**
 * Represents an OpenAI language model, hosted on Azure, such as gpt-3.5-turbo-instruct.
 * However, it's recommended to use {@link AzureOpenAiChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 * <p>
 * Mandatory parameters for initialization are: endpoint, serviceVersion, apiKey and deploymentName.
 * You can also provide your own OpenAIClient instance, if you need more flexibility.
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

    private OpenAIClient client;
    private final String deploymentName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Tokenizer tokenizer;

    public AzureOpenAiLanguageModel(OpenAIClient client,
                                    String deploymentName,
                                    Tokenizer tokenizer,
                                    Double temperature,
                                    Double topP,
                                    Integer maxTokens,
                                    Double presencePenalty,
                                    Double frequencyPenalty) {
        this(deploymentName, tokenizer, temperature, topP, maxTokens, presencePenalty, frequencyPenalty);
        this.client = client;
    }

    public AzureOpenAiLanguageModel(String endpoint,
                                    String serviceVersion,
                                    String apiKey,
                                    String deploymentName,
                                    Tokenizer tokenizer,
                                    Double temperature,
                                    Double topP,
                                    Integer maxTokens,
                                    Double presencePenalty,
                                    Double frequencyPenalty,
                                    Duration timeout,
                                    Integer maxRetries,
                                    ProxyOptions proxyOptions,
                                    boolean logRequestsAndResponses) {
        this(deploymentName, tokenizer, temperature, topP, maxTokens, presencePenalty, frequencyPenalty);
        this.client = setupOpenAIClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, proxyOptions, logRequestsAndResponses);
    }

    private AzureOpenAiLanguageModel(String deploymentName,
                                     Tokenizer tokenizer,
                                     Double temperature,
                                     Double topP,
                                     Integer maxTokens,
                                     Double presencePenalty,
                                     Double frequencyPenalty) {

        this.deploymentName = getOrDefault(deploymentName, "gpt-35-turbo-instruct");
        this.tokenizer = getOrDefault(tokenizer, new OpenAiTokenizer(GPT_3_5_TURBO_INSTRUCT));
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
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

        Completions completions = client.getCompletions(deploymentName, options);

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
        private Tokenizer tokenizer;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIClient openAIClient;

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

        public Builder proxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        /**
         * Sets the Azure OpenAI client. This is an optional parameter, if you need more flexibility than using the endpoint, serviceVersion, apiKey, deploymentName parameters.
         *
         * @param openAIClient The Azure OpenAI client.
         * @return builder
         */
        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        public AzureOpenAiLanguageModel build() {
            if (openAIClient == null) {
                return new AzureOpenAiLanguageModel(
                        endpoint,
                        serviceVersion,
                        apiKey,
                        deploymentName,
                        tokenizer,
                        temperature,
                        topP,
                        maxTokens,
                        presencePenalty,
                        frequencyPenalty,
                        timeout,
                        maxRetries,
                        proxyOptions,
                        logRequestsAndResponses
                );
            } else {
                return new AzureOpenAiLanguageModel(
                        openAIClient,
                        deploymentName,
                        tokenizer,
                        temperature,
                        topP,
                        maxTokens,
                        presencePenalty,
                        frequencyPenalty
                );
            }
        }
    }
}
