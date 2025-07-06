package dev.langchain4j.model.voyageai;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.voyageai.VoyageAiJsonUtils.fromJson;
import static dev.langchain4j.model.voyageai.VoyageAiJsonUtils.toJson;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class VoyageAiClient {

    public static final String DEFAULT_BASE_URL = "https://api.voyageai.com/v1/";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;

    VoyageAiClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(
                        getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), ofSeconds(15)))
                .readTimeout(
                        getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), ofSeconds(60)))
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureTrailingForwardSlash(ensureNotBlank(builder.baseUrl, "baseUrl"));

        Map<String, String> defaultHeaders = new HashMap<>();
        if (builder.apiKey != null) {
            defaultHeaders.put("Authorization", "Bearer " + builder.apiKey);
        }
        if (builder.customHeaders != null) {
            defaultHeaders.putAll(builder.customHeaders);
        }
        this.defaultHeaders = defaultHeaders;
    }

    EmbeddingResponse embed(EmbeddingRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "embeddings")
                .body(toJson(request))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeaders(defaultHeaders)
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        return fromJson(successfulHttpResponse.body(), EmbeddingResponse.class);
    }

    RerankResponse rerank(RerankRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "rerank")
                .body(toJson(request))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeaders(defaultHeaders)
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        return fromJson(successfulHttpResponse.body(), RerankResponse.class);
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private Duration timeout;
        private String apiKey;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        VoyageAiClient build() {
            return new VoyageAiClient(this);
        }
    }
}
