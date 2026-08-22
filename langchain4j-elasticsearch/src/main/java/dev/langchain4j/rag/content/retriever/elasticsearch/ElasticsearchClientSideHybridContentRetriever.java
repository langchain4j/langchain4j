package dev.langchain4j.rag.content.retriever.elasticsearch;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Performs hybrid retrieval without using Elasticsearch's server-side RRF retriever.
 *
 * <p>A kNN search and a full-text search are executed independently and their ranked results are fused in the
 * client using {@link ReciprocalRankFuser}. This is useful for Elasticsearch installations whose license does not
 * include the server-side RRF retriever.
 *
 * <p>The same {@link Filter} is applied to both searches. Branch-specific result limits and score thresholds can be
 * configured because vector and full-text scores are not directly comparable.
 */
public class ElasticsearchClientSideHybridContentRetriever implements ContentRetriever {

    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final int DEFAULT_RRF_RANK_CONSTANT = 60;

    private final ContentRetriever vectorRetriever;
    private final ContentRetriever fullTextRetriever;
    private final int maxResults;
    private final int rrfRankConstant;
    private final Executor executor;

    ElasticsearchClientSideHybridContentRetriever(
            ContentRetriever vectorRetriever,
            ContentRetriever fullTextRetriever,
            int maxResults,
            int rrfRankConstant,
            Executor executor) {
        this.vectorRetriever = ensureNotNull(vectorRetriever, "vectorRetriever");
        this.fullTextRetriever = ensureNotNull(fullTextRetriever, "fullTextRetriever");
        this.maxResults = ensureGreaterThanZero(maxResults, "maxResults");
        this.rrfRankConstant = ensureGreaterThanZero(rrfRankConstant, "rrfRankConstant");
        this.executor = ensureNotNull(executor, "executor");
    }

    private ElasticsearchClientSideHybridContentRetriever(Builder builder) {
        ElasticsearchClient client = ensureNotNull(builder.client, "client");
        EmbeddingModel embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        int vectorMaxResults = ensureGreaterThanZero(
                builder.vectorMaxResults == null ? builder.maxResults : builder.vectorMaxResults, "vectorMaxResults");
        int fullTextMaxResults = ensureGreaterThanZero(
                builder.fullTextMaxResults == null ? builder.maxResults : builder.fullTextMaxResults,
                "fullTextMaxResults");

        ElasticsearchConfigurationKnn vectorConfiguration = ElasticsearchConfigurationKnn.builder()
                .numCandidates(builder.numCandidates)
                .includeVectorResponse(builder.includeVectorResponse)
                .build();
        this.vectorRetriever = ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName(builder.indexName)
                .configuration(vectorConfiguration)
                .embeddingModel(embeddingModel)
                .maxResults(vectorMaxResults)
                .minScore(builder.vectorMinScore)
                .filter(builder.filter)
                .build();
        this.fullTextRetriever = ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName(builder.indexName)
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .maxResults(fullTextMaxResults)
                .minScore(builder.fullTextMinScore)
                .filter(builder.filter)
                .build();
        this.maxResults = ensureGreaterThanZero(builder.maxResults, "maxResults");
        this.rrfRankConstant = ensureGreaterThanZero(builder.rrfRankConstant, "rrfRankConstant");
        this.executor = ensureNotNull(builder.executor, "executor");
    }

    @Override
    public List<Content> retrieve(Query query) {
        CompletableFuture<List<Content>> vectorResults =
                CompletableFuture.supplyAsync(() -> vectorRetriever.retrieve(query), executor);
        CompletableFuture<List<Content>> fullTextResults =
                CompletableFuture.supplyAsync(() -> fullTextRetriever.retrieve(query), executor);

        try {
            return ReciprocalRankFuser.fuse(List.of(vectorResults.join(), fullTextResults.join()), rrfRankConstant)
                    .stream()
                    .limit(maxResults)
                    .toList();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ElasticsearchClient client;
        private String indexName = "default";
        private EmbeddingModel embeddingModel;
        private int maxResults = DEFAULT_MAX_RESULTS;
        private Integer vectorMaxResults;
        private Integer fullTextMaxResults;
        private double vectorMinScore;
        private double fullTextMinScore;
        private Filter filter;
        private Integer numCandidates;
        private boolean includeVectorResponse;
        private int rrfRankConstant = DEFAULT_RRF_RANK_CONSTANT;
        private Executor executor = ForkJoinPool.commonPool();

        public Builder client(ElasticsearchClient client) {
            this.client = client;
            return this;
        }

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /** Sets the maximum number of fused results returned to the caller. */
        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /** Sets the number of candidates retrieved by the vector search before fusion. */
        public Builder vectorMaxResults(int vectorMaxResults) {
            this.vectorMaxResults = vectorMaxResults;
            return this;
        }

        /** Sets the number of candidates retrieved by the full-text search before fusion. */
        public Builder fullTextMaxResults(int fullTextMaxResults) {
            this.fullTextMaxResults = fullTextMaxResults;
            return this;
        }

        public Builder vectorMinScore(double vectorMinScore) {
            this.vectorMinScore = vectorMinScore;
            return this;
        }

        public Builder fullTextMinScore(double fullTextMinScore) {
            this.fullTextMinScore = fullTextMinScore;
            return this;
        }

        /** Sets the metadata filter that is applied to both retrieval branches. */
        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public Builder numCandidates(Integer numCandidates) {
            this.numCandidates = numCandidates;
            return this;
        }

        public Builder includeVectorResponse(boolean includeVectorResponse) {
            this.includeVectorResponse = includeVectorResponse;
            return this;
        }

        public Builder rrfRankConstant(int rrfRankConstant) {
            this.rrfRankConstant = rrfRankConstant;
            return this;
        }

        /** Sets the executor used to run the two blocking Elasticsearch searches. */
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public ElasticsearchClientSideHybridContentRetriever build() {
            return new ElasticsearchClientSideHybridContentRetriever(this);
        }
    }
}
