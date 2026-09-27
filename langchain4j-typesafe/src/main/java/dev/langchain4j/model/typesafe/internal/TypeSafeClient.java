package dev.langchain4j.model.typesafe.internal;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class TypeSafeClient {

    private static final Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
            .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
            .build());

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorizationHeader;

    private TypeSafeClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = httpClientBuilder
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .build();
        if (builder.logRequests || builder.logResponses) {
            this.httpClient =
                    new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = httpClient;
        }
        this.baseUrl = ensureTrailingForwardSlash(ensureNotBlank(builder.baseUrl, "baseUrl"));
        this.authorizationHeader = "Bearer " + ensureNotBlank(builder.apiKey, "apiKey");
    }

    public static Builder builder() {
        return new Builder();
    }

    public TypeSafeResponse decide(Map<String, Object> request) {
        SuccessfulHttpResponse response = httpClient.execute(toHttpRequest(request));
        return CODEC.fromJson(response.body(), TypeSafeResponse.class);
    }

    public CompletableFuture<TypeSafeResponse> decideAsync(Map<String, Object> request) {
        CompletableFuture<SuccessfulHttpResponse> httpFuture = httpClient.executeAsync(toHttpRequest(request));
        CompletableFuture<TypeSafeResponse> result =
                httpFuture.thenApply(response -> CODEC.fromJson(response.body(), TypeSafeResponse.class));
        propagateCancellation(result, httpFuture);
        return result;
    }

    private HttpRequest toHttpRequest(Map<String, Object> request) {
        return HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "v1/systemone")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authorizationHeader)
                .body(CODEC.toJson(request))
                .build();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;
        private Logger logger;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public TypeSafeClient build() {
            return new TypeSafeClient(this);
        }
    }
}
