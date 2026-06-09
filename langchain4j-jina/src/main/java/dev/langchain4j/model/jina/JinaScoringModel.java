package dev.langchain4j.model.jina;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
import dev.langchain4j.model.jina.internal.client.JinaClient;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://jina.ai/reranker">Jina Reranker API</a>.
 */
public class JinaScoringModel implements ScoringModel {

    private static final String DEFAULT_BASE_URL = "https://api.jina.ai/v1/";

    private final JinaClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Deprecated(forRemoval = true, since = "1.4.0")
    public JinaScoringModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Duration timeout,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses) {
        this.client = JinaClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxRetries = getOrDefault(maxRetries, 2);
    }

    public JinaScoringModel(JinaScoringModelBuilder builder) {
        this.client = JinaClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    public static JinaScoringModelBuilder builder() {
        return new JinaScoringModelBuilder();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {

        JinaRerankingRequest request = JinaRerankingRequest.builder()
                .model(modelName)
                .query(query)
                .documents(segments.stream().map(TextSegment::text).collect(toList()))
                .returnDocuments(false) // decreasing response size, do not include text in response
                .build();

        JinaRerankingResponse response = withRetryMappingExceptions(() -> client.rerank(request), maxRetries);

        List<Double> scores = response.results.stream()
                .sorted(comparingInt(result -> result.index))
                .map(result -> result.relevanceScore)
                .collect(toList());

        TokenUsage tokenUsage = new TokenUsage(response.usage.promptTokens, 0, response.usage.totalTokens);
        return Response.from(scores, tokenUsage);
    }

    public static class JinaScoringModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        JinaScoringModelBuilder() {}

        /**
         * Sets the base URL of the Jina Reranker API. Defaults to {@code "https://api.jina.ai/v1/"}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public JinaScoringModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Jina API key used to authenticate requests.
         *
         * @param apiKey the Jina API key
         * @return {@code this}
         */
        public JinaScoringModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the reranking model name, e.g. {@code "jina-reranker-v2-base-multilingual"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public JinaScoringModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 60 seconds.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public JinaScoringModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public JinaScoringModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Enables debug logging of request bodies sent to the Jina API.
         *
         * @param logRequests {@code true} to enable request logging
         * @return {@code this}
         */
        public JinaScoringModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of response bodies received from the Jina API.
         *
         * @param logResponses {@code true} to enable response logging
         * @return {@code this}
         */
        public JinaScoringModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public JinaScoringModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public JinaScoringModel build() {
            return new JinaScoringModel(this);
        }

        public String toString() {
            return "JinaScoringModel.JinaScoringModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey
                    + ", modelName=" + this.modelName + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries
                    + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
