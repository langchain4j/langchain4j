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
 * using the Knn implementation.
 */
public class ElasticsearchConfigurationKnn implements ElasticsearchConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfigurationKnn.class);

    @Override
    public SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                                   String indexName,
                                                   EmbeddingSearchRequest embeddingSearchRequest) throws ElasticsearchException, IOException {
        KnnQuery.Builder krb = new KnnQuery.Builder()
                .field("vector")
                .queryVector(embeddingSearchRequest.queryEmbedding().vectorAsList())
                .numCandidates(embeddingSearchRequest.maxResults());

        if (embeddingSearchRequest.filter() != null) {
            krb.filter(ElasticsearchMetadataFilterMapper.map(embeddingSearchRequest.filter()));
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
