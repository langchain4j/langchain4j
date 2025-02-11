package dev.langchain4j.model.voyageai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.voyageai.VoyageAiApi.DEFAULT_BASE_URL;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

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

    public VoyageAiScoringModel(
            String baseUrl,
            Duration timeout,
            Integer maxRetries,
            String apiKey,
            String modelName,
            Integer topK,
            Boolean truncation,
            Boolean logRequests,
            Boolean logResponses
    ) {
        // Below attributes are force to non-null
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.modelName = ensureNotBlank(modelName, "modelName");
        // Below attributes can be null
        this.truncation = truncation;
        this.topK = topK;

        this.client = VoyageAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        RerankRequest request = RerankRequest.builder()
                .model(modelName)
                .query(query)
                .documents(segments.stream()
                        .map(TextSegment::text)
                        .collect(toList()))
                .topK(topK)
                .truncation(truncation)
                .build();

        RerankResponse response = withRetry(() -> client.rerank(request), maxRetries);

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

        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private String apiKey;
        private String modelName;
        private Integer topK;
        private Boolean truncation;
        private Boolean logRequests;
        private Boolean logResponses;

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

        public VoyageAiScoringModel build() {
            return new VoyageAiScoringModel(baseUrl, timeout, maxRetries, apiKey, modelName, topK, truncation, logRequests, logResponses);
        }
    }
}
