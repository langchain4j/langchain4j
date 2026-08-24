package dev.langchain4j.model.cohere;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.cohere.CohereJsonUtils.fromJson;
import static dev.langchain4j.model.cohere.CohereJsonUtils.toJson;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.net.Proxy;
import java.time.Duration;
import org.slf4j.Logger;

class CohereClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorizationHeader;

    CohereClient(CohereClientBuilder builder) {

        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        Duration timeout = getOrDefault(builder.timeout, ofSeconds(60));

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(timeout)
                .readTimeout(timeout)
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

    public static CohereClientBuilder builder() {
        return new CohereClientBuilder();
    }

    EmbedResponse embed(EmbedRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "embed")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", authorizationHeader)
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);

        return fromJson(response.body(), EmbedResponse.class);
    }

    EmbedV2Response embedV2(EmbedV2Request request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "embed")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", authorizationHeader)
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);

        return fromJson(response.body(), EmbedV2Response.class);
    }

    RerankResponse rerank(RerankRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "rerank")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", authorizationHeader)
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);

        return fromJson(response.body(), RerankResponse.class);
    }

    public static class CohereClientBuilder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        CohereClientBuilder() {
        }

        public CohereClientBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public CohereClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public CohereClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public CohereClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CohereClientBuilder proxy(Proxy proxy) {
            if (proxy != null) {
                throw new UnsupportedOperationException(
                        "Proxy configuration via proxy(...) is no longer supported. Supply a custom "
                                + "HttpClientBuilder via httpClientBuilder(...) to configure a proxy.");
            }
            this.proxy = proxy;
            return this;
        }

        public CohereClientBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public CohereClientBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public CohereClientBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public CohereClient build() {
            return new CohereClient(this);
        }

        public String toString() {
            return "CohereClient.CohereClientBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", timeout=" + this.timeout + ", proxy=" + this.proxy + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
