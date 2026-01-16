package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store
 * using the approximate kNN query implementation.
 *
 * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-knn-query.html#knn-query-top-level-parameters">kNN query</a>
 */
public class ElasticsearchConfigurationKnn extends ElasticsearchConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfigurationKnn.class);
    private final Integer numCandidates;

    public static class Builder {
        private Integer numCandidates;
        private boolean includeVectorResponse = false;

        public ElasticsearchConfigurationKnn build() {
            return new ElasticsearchConfigurationKnn(numCandidates, includeVectorResponse);
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

    public static ElasticsearchConfigurationKnn.Builder builder() {
        return new Builder();
    }

    private ElasticsearchConfigurationKnn(final Integer numCandidates, final boolean includeVectorResponse) {
        this.numCandidates = numCandidates;
        this.includeVectorResponse = includeVectorResponse;
    }

    @Override
    SearchResponse<Document> vectorSearch(
            ElasticsearchClient client, String indexName, EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException, IOException {
        KnnQuery.Builder krb = new KnnQuery.Builder()
                .field(VECTOR_FIELD)
                .queryVector(embeddingSearchRequest.queryEmbedding().vectorAsList());

        if (embeddingSearchRequest.filter() != null) {
            krb.filter(ElasticsearchMetadataFilterMapper.map(embeddingSearchRequest.filter()));
        }

        if (numCandidates != null) {
            krb.numCandidates(numCandidates);
        }

        KnnQuery knn = krb.build();

        log.trace("Searching for embeddings in index [{}] with query [{}].", indexName, knn);

        return client.search(
                s -> s.source(sr -> {
                            if (includeVectorResponse) {
                                return sr.filter(f -> f.excludeVectors(false));
                            }
                            return new SourceConfig.Builder().filter(f -> f);
                        })
                        .index(indexName)
                        .size(embeddingSearchRequest.maxResults())
                        .query(q -> q.knn(knn))
                        .minScore(embeddingSearchRequest.minScore()),
                Document.class);
    }

    @Override
    SearchResponse<Document> fullTextSearch(
            final ElasticsearchClient client, final String indexName, final String textQuery)
            throws ElasticsearchException {
        throw new UnsupportedOperationException("Knn configuration does not support full text search");
    }

    @Override
    SearchResponse<Document> hybridSearch(
            final ElasticsearchClient client,
            final String indexName,
            final EmbeddingSearchRequest embeddingSearchRequest,
            final String textQuery)
            throws ElasticsearchException {
        throw new UnsupportedOperationException("Knn configuration does not support hybrid search");
    }
}
