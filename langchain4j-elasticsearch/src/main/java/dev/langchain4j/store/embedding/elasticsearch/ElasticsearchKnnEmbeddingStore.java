package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store
 * using the Knn implementation.
 */
public class ElasticsearchKnnEmbeddingStore extends AbstractElasticsearchEmbeddingStore {
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
        Query query;
        if (embeddingSearchRequest.filter() == null) {
            query = Query.of(q -> q.matchAll(m -> m));
        } else {
            query = ElasticsearchMetadataFilterMapper.map(embeddingSearchRequest.filter());
        }

        return client.search(sr -> sr
                        .index(indexName)
                        .size(embeddingSearchRequest.maxResults())
                        .query(query)
                        .knn(kr -> kr
                                .field("vector")
                                .queryVector(embeddingSearchRequest.queryEmbedding().vectorAsList())
                                .k(embeddingSearchRequest.maxResults())
                                .numCandidates(embeddingSearchRequest.maxResults())
                        )
                        .minScore(embeddingSearchRequest.minScore() + 1)
                , Document.class);
    }
}
