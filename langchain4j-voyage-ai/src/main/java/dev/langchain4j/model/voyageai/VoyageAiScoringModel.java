package dev.langchain4j.model.voyageai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.voyageai.VoyageAiClient.DEFAULT_BASE_URL;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://docs.voyageai.com/docs/reranker">Voyage AI Rerank API</a>.
 */
public class VoyageAiScoringModel implements ScoringModel {

    private final VoyageAiClient client;
    private final Integer maxRetries;
    private final String modelName;
    private final Integer topK;
    private final Boolean truncation;

    @Deprecated(forRemoval = true, since = "1.4.0")
    public VoyageAiScoringModel(
            HttpClientBuilder httpClientBuilder,
            Map<String, String> customHeaders,
            String baseUrl,
            Duration timeout,
            Integer maxRetries,
            String apiKey,
            String modelName,
            Integer topK,
            Boolean truncation,
            Boolean logRequests,
            Boolean logResponses) {
        // Below attributes are force to non-null
        this.maxRetries = getOrDefault(maxRetries, 2);
        this.modelName = ensureNotBlank(modelName, "modelName");
        // Below attributes can be null
        this.truncation = truncation;
        this.topK = topK;

        this.client = VoyageAiClient.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .customHeaders(() -> customHeaders)
                .build();
    }

    public VoyageAiScoringModel(Builder builder) {
        // Below attributes are force to non-null
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        // Below attributes can be null
        this.truncation = builder.truncation;
        this.topK = builder.topK;

        this.client = VoyageAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .customHeaders(builder.customHeadersSupplier)
                .build();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        RerankRequest request = RerankRequest.builder()
                .model(modelName)
                .query(query)
                .documents(segments.stream().map(TextSegment::text).collect(toList()))
                .topK(topK)
                .truncation(truncation)
                .build();

        RerankResponse response = withRetryMappingExceptions(() -> client.rerank(request), maxRetries);

        List<Double> scores = response.getData().stream()
                .sorted(comparingInt(RerankResponse.RerankData::getIndex))
                .map(RerankResponse.RerankData::getRelevanceScore)
                .collect(toList());

        return Response.from(scores, new TokenUsage(response.getUsage().getTotalTokens()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private String apiKey;
        private String modelName;
        private Integer topK;
        private Boolean truncation;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Name of the model.
         *
         * @param modelName Name of the model.
         * @see VoyageAiScoringModelName
         */
        public Builder modelName(VoyageAiScoringModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * Name of the model.
         *
         * @param modelName Name of the model.
         * @see VoyageAiScoringModelName
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The number of most relevant documents to return. If not specified, the reranking results of all documents will be returned.
         *
         * @param topK the number of most relevant documents to return.
         */
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Whether to truncate the input to satisfy the "context length limit" on the query and the documents. Defaults to true.
         *
         * <ul>
         *     <li>If true, the query and documents will be truncated to fit within the context length limit, before processed by the reranker model.</li>
         *     <li>If false, an error will be raised when the query exceeds 1000 tokens for rerank-lite-1 and 2000 tokens for rerank-1, or the sum of the number of tokens in the query and the number of tokens in any single document exceeds 4000 for rerank-lite-1 and 8000 for rerank-1.</li>
         * </ul>
         *
         * @param truncation Whether to truncate the input to satisfy the "context length limit" on the query and the documents.
         */
        public Builder truncation(Boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public VoyageAiScoringModel build() {
            return new VoyageAiScoringModel(this);
        }
    }
}
