package dev.langchain4j.web.search.searchapi;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.web.search.searchapi.SearchApiJsonUtils.fromJson;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class SearchApiClient {

    private final HttpClient httpClient;
    private final String baseUrl;

    SearchApiClient(SearchApiClientBuilder builder) {
        ensureNotNull(builder.timeout, "timeout");
        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");

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

    public static SearchApiClientBuilder builder() {
        return new SearchApiClientBuilder();
    }

    SearchApiWebSearchResponse search(SearchApiWebSearchRequest request) {
        Map<String, Object> finalParameters = new HashMap<>(request.getFinalOptionalParameters());
        finalParameters.put("engine", request.getEngine());
        finalParameters.put("q", request.getQuery());

        HttpRequest.Builder httpRequestBuilder = HttpRequest.builder()
                .method(GET)
                .url(baseUrl, "api/v1/search")
                .addHeader("Authorization", "Bearer " + request.getApiKey());

        finalParameters.forEach((key, value) -> {
            if (value != null) {
                httpRequestBuilder.addQueryParam(key, String.valueOf(value));
            }
        });

        SuccessfulHttpResponse response = httpClient.execute(httpRequestBuilder.build());

        return fromJson(response.body(), SearchApiWebSearchResponse.class);
    }

    public static class SearchApiClientBuilder {
        private Duration timeout;
        private String baseUrl;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        SearchApiClientBuilder() {
        }

        public SearchApiClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public SearchApiClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public SearchApiClientBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public SearchApiClientBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public SearchApiClientBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public SearchApiClient build() {
            return new SearchApiClient(this);
        }

        public String toString() {
            return "SearchApiClient.SearchApiClientBuilder(timeout=" + this.timeout + ", baseUrl=" + this.baseUrl + ")";
        }
    }
}
