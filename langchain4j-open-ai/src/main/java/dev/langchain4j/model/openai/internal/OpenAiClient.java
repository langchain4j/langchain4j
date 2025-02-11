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

import java.net.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        public String apiVersion;
        public String openAiApiKey;
        public String azureApiKey;
        public Duration connectTimeout = Duration.ofSeconds(60); // TODO remove default?
        public Duration readTimeout = Duration.ofSeconds(60); // TODO remove default?
        public Proxy proxy; // TODO
        public String userAgent;
        public boolean logRequests;
        public boolean logResponses;
        public boolean logStreamingResponses;
        public Path persistTo;
        public Map<String, String> customHeaders;

        public abstract T build();

        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return (B) this;
        }

        /**
         * @param baseUrl Base URL of OpenAI API.
         *                For OpenAI (default): "https://api.openai.com/v1/"
         *                For Azure OpenAI: "https://{resource-name}.openai.azure.com/openai/deployments/{deployment-id}/"
         * @return builder
         */
        public B baseUrl(String baseUrl) {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
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
         * @param apiVersion Version of the API in the YYYY-MM-DD format. Applicable only for Azure OpenAI.
         * @return builder
         */
        public B apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return (B) this;
        }

        /**
         * @param openAiApiKey OpenAI API key.
         *                     Will be injected in HTTP headers like this: "Authorization: Bearer ${openAiApiKey}"
         * @return builder
         */
        public B openAiApiKey(String openAiApiKey) {
            if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "openAiApiKey cannot be null or empty. API keys can be generated here: https://platform.openai.com/account/api-keys"
                );
            }
            this.openAiApiKey = openAiApiKey;
            return (B) this;
        }

        /**
         * @param azureApiKey Azure API key.
         *                    Will be injected in HTTP headers like this: "api-key: ${azureApiKey}"
         * @return builder
         */
        public B azureApiKey(String azureApiKey) {
            if (azureApiKey == null || azureApiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("azureApiKey cannot be null or empty");
            }
            this.azureApiKey = azureApiKey;
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

        // TODO
        public B proxy(Proxy proxy) {
            this.proxy = proxy;
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

        public B logStreamingResponses(Boolean logStreamingResponses) {
            if (logStreamingResponses == null) {
                logStreamingResponses = false;
            }
            this.logStreamingResponses = logStreamingResponses;
            return (B) this;
        }

        /**
         * Generated response will be persisted under <code>java.io.tmpdir</code>. Used with images generation for the moment only.
         * The URL within <code>dev.ai4j.openai4j.image.GenerateImagesResponse</code> will contain the URL to local images then.
         *
         * @return builder
         */
        public B withPersisting() {
            persistTo = Paths.get(System.getProperty("java.io.tmpdir"));
            return (B) this;
        }

        /**
         * Generated response will be persisted under provided path. Used with images generation for the moment only.
         * The URL within <code>dev.ai4j.openai4j.image.GenerateImagesResponse</code> will contain the URL to local images then.
         *
         * @param persistTo path
         * @return builder
         */
        public B persistTo(Path persistTo) {
            this.persistTo = persistTo;
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
