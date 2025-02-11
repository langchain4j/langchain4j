package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.moderation.ModerationRequest;
import dev.langchain4j.model.openai.internal.moderation.ModerationResponse;
import dev.langchain4j.model.openai.internal.spi.OpenAiClientBuilderFactory;
import dev.langchain4j.model.openai.internal.spi.ServiceHelper;

import java.time.Duration;
import java.util.Map;

public abstract class OpenAiClient {

    public abstract SyncOrAsyncOrStreaming<CompletionResponse> completion(CompletionRequest request);

    public abstract SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request);

    public abstract SyncOrAsync<EmbeddingResponse> embedding(EmbeddingRequest request);

    public abstract SyncOrAsync<ModerationResponse> moderation(ModerationRequest request);

    public abstract SyncOrAsync<GenerateImagesResponse> imagesGeneration(GenerateImagesRequest request);

    @SuppressWarnings("rawtypes")
    public static Builder builder() {
        for (OpenAiClientBuilderFactory factory : ServiceHelper.loadFactories(OpenAiClientBuilderFactory.class)) {
            return factory.get();
        }
        // fallback to the default
        return DefaultOpenAiClient.builder();
    }

    @SuppressWarnings("unchecked")
    public abstract static class Builder<T extends OpenAiClient, B extends Builder<T, B>> {

        public HttpClientBuilder httpClientBuilder;
        public String baseUrl;
        public String organizationId;
        public String openAiApiKey;
        public Duration connectTimeout;
        public Duration readTimeout;
        public String userAgent;
        public boolean logRequests;
        public boolean logResponses;
        public Map<String, String> customHeaders;

        public abstract T build();

        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return (B) this;
        }

        /**
         * @param baseUrl Base URL of OpenAI API. For example: "https://api.openai.com/v1/"
         * @return builder
         */
        public B baseUrl(String baseUrl) {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/"; // TODO?
            return (B) this;
        }

        /**
         * @param organizationId The organizationId for OpenAI: https://platform.openai.com/docs/api-reference/organization-optional
         * @return builder
         */
        public B organizationId(String organizationId) {
            this.organizationId = organizationId;
            return (B) this;
        }

        /**
         * @param openAiApiKey OpenAI API key.
         *                     Will be injected in HTTP headers like this: "Authorization: Bearer ${openAiApiKey}"
         * @return builder
         */
        public B openAiApiKey(String openAiApiKey) {
            this.openAiApiKey = openAiApiKey;
            return (B) this;
        }

        public B connectTimeout(Duration connectTimeout) {
            if (connectTimeout == null) {
                throw new IllegalArgumentException("connectTimeout cannot be null");
            }
            this.connectTimeout = connectTimeout;
            return (B) this;
        }

        public B readTimeout(Duration readTimeout) {
            if (readTimeout == null) {
                throw new IllegalArgumentException("readTimeout cannot be null");
            }
            this.readTimeout = readTimeout;
            return (B) this;
        }

        public B userAgent(String userAgent) {
            this.userAgent = userAgent;
            return (B) this;
        }

        public B logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }
            this.logRequests = logRequests;
            return (B) this;
        }

        public B logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }
            this.logResponses = logResponses;
            return (B) this;
        }

        /**
         * Custom headers to be added to each HTTP request.
         *
         * @param customHeaders a map of headers
         * @return builder
         */
        public B customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return (B) this;
        }
    }
}
