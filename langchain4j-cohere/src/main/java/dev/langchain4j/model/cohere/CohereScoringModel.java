package dev.langchain4j.model.cohere;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://docs.cohere.com/docs/rerank-guide">Cohere Rerank API</a>.
 */
public class CohereScoringModel implements ScoringModel {

    private static final String DEFAULT_BASE_URL = "https://api.cohere.ai/v1/";

    private final CohereClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Deprecated(forRemoval = true, since = "1.4.0")
    public CohereScoringModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Duration timeout,
            Integer maxRetries,
            Proxy proxy,
            Boolean logRequests,
            Boolean logResponses) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .proxy(proxy)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = modelName;
        this.maxRetries = getOrDefault(maxRetries, 2);
    }

    public CohereScoringModel(CohereScoringModelBuilder builder) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .proxy(builder.proxy)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.modelName = builder.modelName;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     */
    @Deprecated(forRemoval = true)
    public static CohereScoringModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static CohereScoringModelBuilder builder() {
        return new CohereScoringModelBuilder();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {

        RerankRequest request = RerankRequest.builder()
                .model(modelName)
                .query(query)
                .documents(segments.stream().map(TextSegment::text).collect(toList()))
                .build();

        RerankResponse response = withRetryMappingExceptions(() -> client.rerank(request), maxRetries);

        List<Double> scores = response.getResults().stream()
                .sorted(comparingInt(Result::getIndex))
                .map(Result::getRelevanceScore)
                .collect(toList());

        return Response.from(
                scores, new TokenUsage(response.getMeta().getBilledUnits().getSearchUnits()));
    }

    public static class CohereScoringModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        CohereScoringModelBuilder() {}

        /**
         * Sets the base URL of the Cohere API. Defaults to {@code "https://api.cohere.ai/v1/"}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public CohereScoringModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Cohere API key used to authenticate requests.
         *
         * @param apiKey the Cohere API key
         * @return {@code this}
         */
        public CohereScoringModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the reranking model name, e.g. {@code "rerank-english-v3.0"} or
         * {@code "rerank-multilingual-v3.0"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public CohereScoringModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 60 seconds.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public CohereScoringModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public CohereScoringModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the HTTP proxy to use for outbound requests.
         *
         * @param proxy the proxy
         * @return {@code this}
         */
        public CohereScoringModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        /**
         * Enables debug logging of request bodies sent to the Cohere API.
         *
         * @param logRequests {@code true} to enable request logging
         * @return {@code this}
         */
        public CohereScoringModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of response bodies received from the Cohere API.
         *
         * @param logResponses {@code true} to enable response logging
         * @return {@code this}
         */
        public CohereScoringModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public CohereScoringModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public CohereScoringModel build() {
            return new CohereScoringModel(this);
        }

        public String toString() {
            return "CohereScoringModel.CohereScoringModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey
                    + ", modelName=" + this.modelName + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries
                    + ", proxy=" + this.proxy + ", logRequests=" + this.logRequests + ", logResponses="
                    + this.logResponses + ")";
        }
    }
}
