package dev.langchain4j.web.search.brave;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.web.search.brave.BraveJsonUtils.fromJson;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class BraveClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    BraveClient(BraveClientBuilder builder) {
        ensureNotNull(builder.timeout, "timeout");
        ensureNotBlank(builder.baseUrl, "baseUrl");
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.baseUrl = builder.baseUrl;

        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }
    }

    public static BraveClientBuilder builder() {
        return new BraveClientBuilder();
    }

    BraveWebSearchResponse search(BraveWebSearchRequest request) {
        ensureNotBlank(request.getQuery(), "query");

        HttpRequest.Builder httpRequestBuilder = HttpRequest.builder()
                .method(GET)
                .url(baseUrl, "res/v1/web/search")
                .addHeader("Accept", "application/json")
                .addHeader("X-Subscription-Token", apiKey);

        // Additional parameters are provided first, so that the core parameters take precedence on conflict.
        Map<String, Object> parameters = new HashMap<>();
        if (request.getAdditionalParameters() != null) {
            parameters.putAll(request.getAdditionalParameters());
        }
        putIfNotNull(parameters, "q", request.getQuery());
        putIfNotNull(parameters, "count", request.getCount());
        putIfNotNull(parameters, "offset", request.getOffset());
        putIfNotNull(parameters, "search_lang", request.getLanguage());
        putIfNotNull(parameters, "country", request.getCountry());
        putIfNotNull(parameters, "safesearch", request.getSafesearch());

        parameters.forEach((key, value) -> {
            if (value != null) {
                httpRequestBuilder.addQueryParam(key, String.valueOf(value));
            }
        });

        SuccessfulHttpResponse response = httpClient.execute(httpRequestBuilder.build());

        return fromJson(response.body(), BraveWebSearchResponse.class);
    }

    private static void putIfNotNull(Map<String, Object> parameters, String key, Object value) {
        if (value != null) {
            parameters.put(key, value);
        }
    }

    public static class BraveClientBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        BraveClientBuilder() {}

        public BraveClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public BraveClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public BraveClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public BraveClientBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public BraveClientBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public BraveClientBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public BraveClient build() {
            return new BraveClient(this);
        }

        public String toString() {
            return "BraveClient.BraveClientBuilder(baseUrl=" + this.baseUrl + ", apiKey="
                    + (this.apiKey == null ? null : "********") + ", timeout=" + this.timeout + ")";
        }
    }
}
