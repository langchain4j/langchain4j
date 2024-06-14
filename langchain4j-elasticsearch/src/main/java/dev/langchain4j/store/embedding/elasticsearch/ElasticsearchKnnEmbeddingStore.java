package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store
 * using the Knn implementation.
 */
public class ElasticsearchKnnEmbeddingStore extends AbstractElasticsearchEmbeddingStore {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchKnnEmbeddingStore.class);

    /**
     * Creates an instance of ElasticsearchEmbeddingStore.
     *
     * @param serverUrl Elasticsearch Server URL (mandatory)
     * @param apiKey    Elasticsearch API key (optional)
     * @param userName  Elasticsearch userName (optional)
     * @param password  Elasticsearch password (optional)
     * @param indexName Elasticsearch index name (optional). Default value: "default".
     *                  Index will be created automatically if not exists.
     */
    public ElasticsearchKnnEmbeddingStore(String serverUrl,
                                       String apiKey,
                                       String userName,
                                       String password,
                                       String indexName) {
        super(serverUrl, apiKey, userName, password, indexName);
    }

    public ElasticsearchKnnEmbeddingStore(RestClient restClient, String indexName) {
        super(restClient, indexName);
    }

    public static ElasticsearchKnnEmbeddingStore.Builder builder() {
        return new ElasticsearchKnnEmbeddingStore.Builder();
    }

    public static class Builder extends AbstractElasticsearchEmbeddingStore.Builder {
        public ElasticsearchKnnEmbeddingStore build() {
            if (restClient != null) {
                return new ElasticsearchKnnEmbeddingStore(restClient, indexName);
            } else {
                return new ElasticsearchKnnEmbeddingStore(serverUrl, apiKey, userName, password, indexName);
            }
        }
    }

    @Override
    public SearchResponse<Document> internalSearch(EmbeddingSearchRequest embeddingSearchRequest) throws ElasticsearchException, IOException {
        KnnQuery.Builder krb = new KnnQuery.Builder()
                .field("vector")
                .queryVector(embeddingSearchRequest.queryEmbedding().vectorAsList())
                .k(embeddingSearchRequest.maxResults())
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
                        .minScore(embeddingSearchRequest.minScore() + 1)
                , Document.class);
    }
}
