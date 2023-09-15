package dev.langchain4j.model.azure;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionChoice;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.output.Response;

import java.net.Proxy;
import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.finishReasonFrom;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.tokenUsageFrom;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI language model, hosted on Azure, such as text-davinci-003.
 * However, it's recommended to use {@link AzureOpenAiChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 * <p>
 * Mandatory parameters for initialization are: baseUrl, apiVersion and apiKey.
 * <p>
 * There are two primary authentication methods to access Azure OpenAI:
 * <p>
 * 1. API Key Authentication: For this type of authentication, HTTP requests must include the
 * API Key in the "api-key" HTTP header.
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

    private final OpenAiClient client;
    private final Double temperature;
    private final Integer maxRetries;
    private final Tokenizer tokenizer;

    public AzureOpenAiLanguageModel(String baseUrl,
                                    String apiVersion,
                                    String apiKey,
                                    Tokenizer tokenizer,
                                    Double temperature,
                                    Duration timeout,
                                    Integer maxRetries,
                                    Proxy proxy,
                                    Boolean logRequests,
                                    Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(60) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .azureApiKey(apiKey)
                .apiVersion(apiVersion)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.temperature = temperature;
        this.maxRetries = maxRetries;
        this.tokenizer = tokenizer;
    }

    @Override
    public Response<String> generate(String prompt) {

        CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .temperature(temperature)
                .build();

        CompletionResponse response = withRetry(() -> client.completion(request).execute(), maxRetries);

        CompletionChoice completionChoice = response.choices().get(0);
        return Response.from(
                completionChoice.text(),
                tokenUsageFrom(response.usage()),
                finishReasonFrom(completionChoice.finishReason())
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

        private String baseUrl;
        private String apiVersion;
        private String apiKey;
        private Tokenizer tokenizer;
        private Double temperature;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;

        /**
         * Sets the Azure OpenAI base URL. This is a mandatory parameter.
         *
         * @param baseUrl The Azure OpenAI base URL in the format: https://{resource}.openai.azure.com/openai/deployments/{deployment}
         * @return builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Azure OpenAI API version. This is a mandatory parameter.
         *
         * @param apiVersion The Azure OpenAI api version in the format: 2023-05-15
         * @return builder
         */
        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
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

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
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

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public AzureOpenAiLanguageModel build() {
            return new AzureOpenAiLanguageModel(
                    baseUrl,
                    apiVersion,
                    apiKey,
                    tokenizer,
                    temperature,
                    timeout,
                    maxRetries,
                    proxy,
                    logRequests,
                    logResponses
            );
        }
    }
}
