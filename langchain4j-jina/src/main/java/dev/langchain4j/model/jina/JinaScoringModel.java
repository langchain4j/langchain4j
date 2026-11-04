package dev.langchain4j.model.jina;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
import dev.langchain4j.model.jina.internal.client.JinaClient;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.request.ScoringRequestParameters;
import dev.langchain4j.model.scoring.response.ScoringResponse;
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
    private final ScoringRequestParameters defaultRequestParameters;
    private final Integer maxRetries;
    private final List<ScoringModelListener> listeners;

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
        this.defaultRequestParameters = ScoringRequestParameters.builder().modelName(this.modelName).build();
        this.maxRetries = getOrDefault(maxRetries, 2);
        this.listeners = List.of();
    }

    public JinaScoringModel(JinaScoringModelBuilder builder) {
        this.client = JinaClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.defaultRequestParameters = ScoringRequestParameters.builder().modelName(this.modelName).build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.listeners = copy(builder.listeners);
    }

    public static JinaScoringModelBuilder builder() {
        return new JinaScoringModelBuilder();
    }

    @Override
    public ScoringRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ScoringModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.JINA;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        ensureNotNull(segments, "segments");
        ScoringResponse response = score(ScoringRequest.builder()
                .documents(segments.stream().map(TextSegment::text).toList())
                .query(query)
                .build());
        return Response.from(response.scores(), response.tokenUsage());
    }

    @Override
    public ScoringResponse doScore(ScoringRequest scoringRequest) {

        JinaRerankingRequest request = JinaRerankingRequest.builder()
                .model(getOrDefault(scoringRequest.modelName(), modelName))
                .query(scoringRequest.query())
                .documents(scoringRequest.documents())
                .returnDocuments(false) // decreasing response size, do not include text in response
                .build();

        JinaRerankingResponse response = withRetryMappingExceptions(() -> client.rerank(request), maxRetries);

        List<Double> scores = response.results.stream()
                .sorted(comparingInt(result -> result.index))
                .map(result -> result.relevanceScore)
                .collect(toList());

        TokenUsage tokenUsage = new TokenUsage(response.usage.promptTokens, 0, response.usage.totalTokens);
        return ScoringResponse.builder()
                .scores(scores)
                .modelName(getOrDefault(response.model, getOrDefault(scoringRequest.modelName(), modelName)))
                .tokenUsage(tokenUsage)
                .build();
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
        private HttpClientBuilder httpClientBuilder;
        private List<ScoringModelListener> listeners;

        JinaScoringModelBuilder() {}

        public JinaScoringModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public JinaScoringModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public JinaScoringModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JinaScoringModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public JinaScoringModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public JinaScoringModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

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

        /**
         * Sets a custom HTTP client builder, allowing fine-grained control over the HTTP client
         * configuration such as timeouts and proxy settings.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public JinaScoringModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the {@link ScoringModelListener}s notified before each scoring request, after each response and on
         * each error.
         *
         * @param listeners the listeners
         * @return {@code this}
         */
        public JinaScoringModelBuilder listeners(List<ScoringModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Sets the {@link ScoringModelListener}s notified before each scoring request, after each response and on
         * each error.
         *
         * @param listeners the listeners
         * @return {@code this}
         */
        public JinaScoringModelBuilder listeners(ScoringModelListener... listeners) {
            return listeners(List.of(listeners));
        }

        public JinaScoringModel build() {
            return new JinaScoringModel(this);
        }

        public String toString() {
            return "JinaScoringModel.JinaScoringModelBuilder(baseUrl=" + this.baseUrl + ", apiKey="
                    + (this.apiKey == null ? null : "********") + ", modelName=" + this.modelName + ", timeout="
                    + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests
                    + ", logResponses=" + this.logResponses + ")";
        }
    }
}
