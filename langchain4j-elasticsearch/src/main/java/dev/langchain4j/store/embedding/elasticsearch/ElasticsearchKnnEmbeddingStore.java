package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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
    public SearchResponse<Document> internalSearch(Embedding referenceEmbedding, int maxResults, double minScore) throws ElasticsearchException, IOException {
        return client.search(sr -> sr
                        .index(indexName)
                        .size(maxResults)
                        .query(q -> q.matchAll(maq -> maq))
                        .knn(kr -> kr
                                .field("vector")
                                .queryVector(referenceEmbedding.vectorAsList())
                                .k(maxResults)
                                .numCandidates(maxResults)
                        )
                        .minScore(minScore + 1)
                , Document.class);
    }

    protected List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(SearchResponse<Document> response) {
        return response.hits().hits().stream()
                .map(hit -> Optional.ofNullable(hit.source())
                        .map(document -> new EmbeddingMatch<>(
                                hit.score() - 1.0,
                                hit.id(),
                                new Embedding(document.getVector()),
                                document.getText() == null
                                        ? null
                                        : TextSegment.from(document.getText(), new Metadata(document.getMetadata()))
                        )).orElse(null))
                .collect(toList());
    }
}
