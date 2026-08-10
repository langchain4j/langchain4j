package dev.langchain4j.model.jina.internal.client;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.jina.internal.client.JinaJsonUtils.fromJson;
import static dev.langchain4j.model.jina.internal.client.JinaJsonUtils.toJson;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.model.jina.internal.api.JinaEmbeddingRequest;
import dev.langchain4j.model.jina.internal.api.JinaEmbeddingResponse;
import dev.langchain4j.model.jina.internal.api.JinaMultimodalEmbeddingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
import java.time.Duration;
import org.slf4j.Logger;

public class JinaClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorizationHeader;

    JinaClient(JinaClientBuilder builder) {
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

        this.baseUrl = ensureTrailingForwardSlash(builder.baseUrl);
        this.authorizationHeader = "Bearer " + ensureNotBlank(builder.apiKey, "apiKey");
    }

    public static JinaClientBuilder builder() {
        return new JinaClientBuilder();
    }

    public JinaEmbeddingResponse embed(JinaEmbeddingRequest request) {
        return post("v1/embeddings", request, JinaEmbeddingResponse.class);
    }

    public JinaEmbeddingResponse embedMultimodal(JinaMultimodalEmbeddingRequest request) {
        return post("v1/embeddings", request, JinaEmbeddingResponse.class);
    }

    public JinaRerankingResponse rerank(JinaRerankingRequest request) {
        return post("rerank", request, JinaRerankingResponse.class);
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + path)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authorizationHeader)
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);

        return fromJson(response.body(), responseType);
    }

    public static class JinaClientBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;
        private Logger logger;
        private HttpClientBuilder httpClientBuilder;

        JinaClientBuilder() {}

        public JinaClientBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public JinaClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public JinaClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public JinaClientBuilder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public JinaClientBuilder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public JinaClientBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public JinaClientBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public JinaClient build() {
            return new JinaClient(this);
        }

        public String toString() {
            return "JinaClient.JinaClientBuilder(baseUrl=" + this.baseUrl + ", apiKey="
                    + (this.apiKey == null ? null : "********") + ", timeout=" + this.timeout + ", logRequests="
                    + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
