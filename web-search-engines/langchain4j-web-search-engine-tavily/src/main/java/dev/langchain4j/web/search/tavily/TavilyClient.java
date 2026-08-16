package dev.langchain4j.web.search.tavily;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.web.search.tavily.TavilyJsonUtils.fromJson;
import static dev.langchain4j.web.search.tavily.TavilyJsonUtils.toJson;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;

class TavilyClient {

    private final HttpClient httpClient;
    private final String baseUrl;

    public TavilyClient(TavilyClientBuilder builder) {
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

        this.baseUrl = builder.baseUrl;
    }

    public static TavilyClientBuilder builder() {
        return new TavilyClientBuilder();
    }

    public TavilyResponse search(TavilySearchRequest searchRequest) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "search")
                .addHeader("Content-Type", "application/json")
                .body(toJson(searchRequest))
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);

        return fromJson(response.body(), TavilyResponse.class);
    }

    public static class TavilyClientBuilder {
        private String baseUrl;
        private Duration timeout;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        TavilyClientBuilder() {
        }

        public TavilyClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public TavilyClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public TavilyClientBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public TavilyClientBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public TavilyClientBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public TavilyClient build() {
            return new TavilyClient(this);
        }

        public String toString() {
            return "TavilyClient.TavilyClientBuilder(baseUrl=" + this.baseUrl + ", timeout=" + this.timeout + ")";
        }
    }
}
