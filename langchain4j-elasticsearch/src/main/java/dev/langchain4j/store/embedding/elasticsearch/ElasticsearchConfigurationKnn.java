package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store
 * using the approximate kNN query implementation.
 * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-knn-query.html#knn-query-top-level-parameters">kNN query</a>
 */
public class ElasticsearchConfigurationKnn extends ElasticsearchConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfigurationKnn.class);
    private final Integer numCandidates;

    public static class Builder {
        private Integer numCandidates;

        public ElasticsearchConfigurationKnn build() {
            return new ElasticsearchConfigurationKnn(numCandidates);
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
    }

    public static ElasticsearchConfigurationKnn.Builder builder() {
        return new Builder();
    }


    private ElasticsearchConfigurationKnn(Integer numCandidates) {
        this.numCandidates = numCandidates;
    }

    @Override
    SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                                   String indexName,
                                                   EmbeddingSearchRequest embeddingSearchRequest) throws ElasticsearchException, IOException {
        KnnQuery.Builder krb = new KnnQuery.Builder()
                .field("vector")
                .queryVector(embeddingSearchRequest.queryEmbedding().vectorAsList());

        if (embeddingSearchRequest.filter() != null) {
            krb.filter(ElasticsearchMetadataFilterMapper.map(embeddingSearchRequest.filter()));
        }

        if (numCandidates != null) {
            krb.numCandidates(numCandidates);
        }

        KnnQuery knn = krb.build();

        log.trace("Searching for embeddings in index [{}] with query [{}].", indexName, knn);

        return client.search(sr -> sr
                        .index(indexName)
                        .size(embeddingSearchRequest.maxResults())
                        .query(q -> q.knn(knn))
                        .minScore(embeddingSearchRequest.minScore())
                , Document.class);
    }
}
