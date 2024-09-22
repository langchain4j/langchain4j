package dev.langchain4j.model.voyage;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.voyage.VoyageApi.DEFAULT_BASE_URL;
import static dev.langchain4j.model.voyage.VoyageScoringModelName.RERANK_LITE_1;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://docs.voyageai.com/docs/reranker">Voyage AI Rerank API</a>.
 */
public class VoyageScoringModel implements ScoringModel {

    private final VoyageClient client;
    private final Integer maxRetries;
    private final VoyageScoringModelName modelName;
    private final Integer topK;
    private final Boolean returnDocuments;
    private final Boolean truncation;

    public VoyageScoringModel(
            String baseUrl,
            Duration timeout,
            Integer maxRetries,
            String apiKey,
            VoyageScoringModelName modelName,
            Integer topK,
            Boolean returnDocuments,
            Boolean truncation,
            Boolean logRequests,
            Boolean logResponses
    ) {
        // Below attributes are force to non-null
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.modelName = getOrDefault(modelName, RERANK_LITE_1);
        this.truncation = getOrDefault(truncation, true);
        this.returnDocuments = getOrDefault(returnDocuments, false);
        // Below attributes can be null
        this.topK = topK;

        this.client = VoyageClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static VoyageScoringModel withApiKey(String apiKey) {
        return VoyageScoringModel.builder().apiKey(apiKey).build();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        RerankRequest request = RerankRequest.builder()
                .model(modelName.toString())
                .query(query)
                .documents(segments.stream()
                        .map(TextSegment::text)
                        .collect(toList()))
                .topK(topK)
                .returnDocuments(returnDocuments)
                .truncation(truncation)
                .build();

        RerankResponse response = withRetry(() -> client.rerank(request), maxRetries);

        List<Double> scores = response.getData().stream()
                .sorted(comparingInt(RerankResponse.RerankData::getIndex))
                .map(RerankResponse.RerankData::getRelevanceScore)
                .collect(toList());

        return Response.from(scores, new TokenUsage(response.getUsage().getTotalTokens()));
    }

    public static VoyageScoringModelBuilder builder() {
        return new VoyageScoringModelBuilder();
    }

    public static class VoyageScoringModelBuilder {

        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private String apiKey;
        private VoyageScoringModelName modelName;
        private Integer topK;
        private Boolean returnDocuments;
        private Boolean truncation;
        private Boolean logRequests;
        private Boolean logResponses;

        public VoyageScoringModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public VoyageScoringModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public VoyageScoringModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public VoyageScoringModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Name of the model.
         *
         * @param modelName Name of the model.
         * @see VoyageScoringModelName
         */
        public VoyageScoringModelBuilder modelName(VoyageScoringModelName modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The number of most relevant documents to return. If not specified, the reranking results of all documents will be returned.
         *
         * @param topK the number of most relevant documents to return.
         */
        public VoyageScoringModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Whether to return the documents in the response. Defaults to false.
         *
         * <ul>
         *     <li>If false, the API will return a list of {"index", "relevance_score"} where "index" refers to the index of a document within the input list.</li>
         *     <li>If true, the API will return a list of {"index", "document", "relevance_score"} where "document" is the corresponding document from the input list.</li>
         * </ul>
         *
         * <p><b>NOTE:</b> this parameter has no effect on langchain4j, because {@link ScoringModel#score(TextSegment, String)} will only return the relevance score.</p>
         *
         * @param returnDocuments Whether to return the documents in the response.
         */
        public VoyageScoringModelBuilder returnDocuments(Boolean returnDocuments) {
            this.returnDocuments = returnDocuments;
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
        public VoyageScoringModelBuilder truncation(Boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        public VoyageScoringModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public VoyageScoringModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public VoyageScoringModel build() {
            return new VoyageScoringModel(baseUrl, timeout, maxRetries, apiKey, modelName, topK, returnDocuments, truncation, logRequests, logResponses);
        }
    }
}
