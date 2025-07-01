package dev.langchain4j.model.jina;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
import dev.langchain4j.model.jina.internal.client.JinaClient;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://jina.ai/reranker">Jina Reranker API</a>.
 */
public class JinaScoringModel implements ScoringModel {

    private static final String DEFAULT_BASE_URL = "https://api.jina.ai/v1/";

    private final JinaClient client;
    private final String modelName;
    private final Integer maxRetries;

    public JinaScoringModel(String baseUrl,
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

    public static JinaScoringModelBuilder builder() {
        return new JinaScoringModelBuilder();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {

        JinaRerankingRequest request = JinaRerankingRequest.builder()
                .model(modelName)
                .query(query)
                .documents(segments.stream()
                        .map(TextSegment::text)
                        .collect(toList()))
                .returnDocuments(false)  // decreasing response size, do not include text in response
                .build();

        JinaRerankingResponse response = withRetryMappingExceptions(() -> client.rerank(request), maxRetries);

        List<Double> scores = response.results.stream()
                .sorted(comparingInt(result -> result.index))
                .map(result -> result.relevanceScore)
                .collect(toList());

        TokenUsage tokenUsage = new TokenUsage(
                response.usage.promptTokens,
                0,
                response.usage.totalTokens
        );
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

        JinaScoringModelBuilder() {
        }

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

        public JinaScoringModel build() {
            return new JinaScoringModel(this.baseUrl, this.apiKey, this.modelName, this.timeout, this.maxRetries, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "JinaScoringModel.JinaScoringModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", modelName=" + this.modelName + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
