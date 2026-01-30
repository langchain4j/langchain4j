package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.KnnRetriever;
import co.elastic.clients.elasticsearch._types.Retriever;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store
 * using rff to combine a kNN query and a full text search.
 *
 * @see <a href="https://www.elastic.co/search-labs/tutorials/search-tutorial/vector-search/hybrid-search">hybrid search</a>
 * <br>
 * Running hybrid search requires an elasticsearch paid license.
 *
 * @see <a href="https://www.elastic.co/subscriptions">subscriptions</a>
 */
public class ElasticsearchConfigurationHybrid extends ElasticsearchConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfigurationHybrid.class);
    private final Integer numCandidates;

    public static class Builder {
        private Integer numCandidates;
        private boolean includeVectorResponse = false;

        public ElasticsearchConfigurationHybrid build() {
            return new ElasticsearchConfigurationHybrid(numCandidates, includeVectorResponse);
        }

        /**
         * The number of nearest neighbor candidates to consider per shard while doing knn search.
         * Cannot exceed 10,000. Increasing num_candidates tends to improve the accuracy of the final
         * results.
         *
         * @param numCandidates The number of nearest neighbor candidates to consider per shard
         * @return the builder instance
         */
        public Builder numCandidates(Integer numCandidates) {
            this.numCandidates = numCandidates;
            return this;
        }

        /**
         * Whether to include vector fields in the search response (from Elasticsearch 9.2).
         *
         * @param includeVectorResponse true to include vector fields, false otherwise
         * @return the builder instance
         */
        public Builder includeVectorResponse(boolean includeVectorResponse) {
            this.includeVectorResponse = includeVectorResponse;
            return this;
        }
    }

    public static ElasticsearchConfigurationHybrid.Builder builder() {
        return new Builder();
    }

    private ElasticsearchConfigurationHybrid(final Integer numCandidates, final boolean includeVectorResponse) {
        this.numCandidates = numCandidates;
        this.includeVectorResponse = includeVectorResponse;
    }

    @Override
    SearchResponse<Document> vectorSearch(
            ElasticsearchClient client, String indexName, EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException {
        throw new UnsupportedOperationException("Hybrid configuration does not support vector search");
    }

    @Override
    SearchResponse<Document> fullTextSearch(
            final ElasticsearchClient client, final String indexName, final String textQuery)
            throws ElasticsearchException {
        throw new UnsupportedOperationException("Hybrid configuration does not support full text search");
    }

    @Override
    SearchResponse<Document> hybridSearch(
            final ElasticsearchClient client,
            final String indexName,
            final EmbeddingSearchRequest embeddingSearchRequest,
            final String textQuery)
            throws ElasticsearchException, IOException {

        // Building KNN part of the hybrid query
        KnnRetriever.Builder krb = new KnnRetriever.Builder()
                .field(VECTOR_FIELD)
                .queryVector(embeddingSearchRequest.queryEmbedding().vectorAsList());

        if (embeddingSearchRequest.filter() != null) {
            krb.filter(ElasticsearchMetadataFilterMapper.map(embeddingSearchRequest.filter()));
        }

        // k and numCandidates are required in KnnRetriever, calculating default values similarly to how elasticsearch
        // calculates them for KnnQuery
        if (numCandidates != null) {
            krb.numCandidates(numCandidates);
            krb.k(Math.min(numCandidates, embeddingSearchRequest.maxResults()));
        } else {
            krb.numCandidates(embeddingSearchRequest.maxResults());
            krb.k(embeddingSearchRequest.maxResults());
        }

        KnnRetriever knn = krb.build();

        // Building full text part of the hybrid query
        MatchQuery matchQuery =
                new MatchQuery.Builder().field(TEXT_FIELD).query(textQuery).build();

        log.trace("Searching for embeddings in index [{}] with hybrid query [{}], [{}].", indexName, knn, matchQuery);

        return client.search(
                s -> s.source(sr -> {
                            if (includeVectorResponse) {
                                return sr.filter(f -> f.excludeVectors(false));
                            }
                            return new SourceConfig.Builder().filter(f -> f);
                        })
                        .index(indexName)
                        .retriever(r -> r.rrf(rf -> rf.retrievers(List.of(
                                Retriever.of(rt -> rt.standard(st -> st.query(matchQuery))),
                                Retriever.of(rt -> rt.knn(knn))))))
                        .size(embeddingSearchRequest.maxResults())
                        .minScore(embeddingSearchRequest.minScore()),
                Document.class);
    }
}
