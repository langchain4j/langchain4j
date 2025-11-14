package dev.langchain4j.store.embedding.elasticsearch;

import java.io.IOException;
import java.util.List;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.KnnRetriever;
import co.elastic.clients.elasticsearch._types.Retriever;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store
 * using the approximate kNN query implementation.
 *
 * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-knn-query.html#knn-query-top-level-parameters">kNN query</a>
 */
public class ElasticsearchConfigurationHybrid extends ElasticsearchConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfigurationHybrid.class);
    private final Integer numCandidates;

    public static class Builder {
        private Integer numCandidates;


        public ElasticsearchConfigurationHybrid build() {
            return new ElasticsearchConfigurationHybrid(numCandidates);
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

    public static ElasticsearchConfigurationHybrid.Builder builder() {
        return new Builder();
    }


    private ElasticsearchConfigurationHybrid(Integer numCandidates) {
        this.numCandidates = numCandidates;
    }

    @Override
    SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                            String indexName,
                                            EmbeddingSearchRequest embeddingSearchRequest) throws ElasticsearchException {
        return internalSearch(client, indexName, embeddingSearchRequest, false);
    }

    @Override
    SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                            String indexName,
                                            EmbeddingSearchRequest embeddingSearchRequest,
                                            boolean includeVectorResponse) throws ElasticsearchException {
        throw new UnsupportedOperationException("Hybrid configuration does not support vector search");
    }

    @Override
    SearchResponse<Document> internalSearch(final ElasticsearchClient client, final String indexName, final String textQuery) throws ElasticsearchException {
        throw new UnsupportedOperationException("Hybrid configuration does not support full text search");
    }

    @Override
    SearchResponse<Document> internalSearch(final ElasticsearchClient client, final String indexName, final EmbeddingSearchRequest embeddingSearchRequest, final String textQuery, final boolean includeVectorResponse) throws ElasticsearchException, IOException {
        // Building KNN part of the hybrid query
        KnnRetriever.Builder krb = new KnnRetriever.Builder()
                .field("vector")
                .queryVector(embeddingSearchRequest.queryEmbedding().vectorAsList());

        if (embeddingSearchRequest.filter() != null) {
            krb.filter(ElasticsearchMetadataFilterMapper.map(embeddingSearchRequest.filter()));
        }

        if (numCandidates != null) {
            krb.numCandidates(numCandidates);
        }

        KnnRetriever knn = krb.build();

        // Building full text part of the hybrid query
        // TODO extract vector and text as constants
        MatchQuery matchQuery = new MatchQuery.Builder()
                .field("text")
                .query(textQuery)
                .build();


        log.trace("Searching for embeddings in index [{}] with query [{}].", indexName, knn);

        return client.search(s -> s
                        .source(sr -> {
                            if (includeVectorResponse) {
                                return sr.filter(f -> f.excludeVectors(false));
                            }
                            return new SourceConfig.Builder().filter(f -> f);
                        })
                        .index(indexName)
                        .retriever(r -> r
                                .rrf(rf -> rf
                                        .retrievers(List.of(
                                                Retriever.of(rt -> rt
                                                        .standard(st -> st
                                                                .query(matchQuery)
                                                        )
                                                ),
                                                Retriever.of(rt -> rt.knn(knn))
                                        ))
                                )
                        )
                        .size(embeddingSearchRequest.maxResults())
                        .minScore(embeddingSearchRequest.minScore())
                , Document.class);
    }
}
