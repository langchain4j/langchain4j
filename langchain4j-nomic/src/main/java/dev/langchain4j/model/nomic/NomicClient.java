package dev.langchain4j.model.nomic;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.nomic.NomicJsonUtils.fromJson;
import static dev.langchain4j.model.nomic.NomicJsonUtils.toJson;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import org.slf4j.Logger;

class NomicClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorizationHeader;

    NomicClient(NomicClientBuilder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses) {
            this.httpClient =
                    new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureTrailingForwardSlash(builder.baseUrl);
        this.authorizationHeader = "Bearer " + ensureNotBlank(builder.apiKey, "apiKey");
    }

    public static NomicClientBuilder builder() {
        return new NomicClientBuilder();
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "embedding/text")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authorizationHeader)
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);

        return fromJson(response.body(), EmbeddingResponse.class);
    }

    public static class NomicClientBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private HttpClientBuilder httpClientBuilder;

        NomicClientBuilder() {
        }

        public NomicClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public NomicClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public NomicClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public NomicClientBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public NomicClientBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public NomicClientBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public NomicClientBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public NomicClient build() {
            return new NomicClient(this);
        }

        public String toString() {
            return "NomicClient.NomicClientBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + (this.apiKey == null ? null : "********") + ", timeout=" + this.timeout + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
