package dev.langchain4j.store.embedding.elasticsearch;

import java.io.IOException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store
 * using the approximate kNN query implementation.
 *
 * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-knn-query.html#knn-query-top-level-parameters">kNN query</a>
 */
public class ElasticsearchConfigurationFullText extends ElasticsearchConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfigurationFullText.class);

    public static class Builder {

        public ElasticsearchConfigurationFullText build() {
            return new ElasticsearchConfigurationFullText();
        }
    }

    public static ElasticsearchConfigurationFullText.Builder builder() {
        return new ElasticsearchConfigurationFullText.Builder();
    }

    @Override
    SearchResponse<Document> internalSearch(final ElasticsearchClient client, final String indexName, final EmbeddingSearchRequest embeddingSearchRequest) throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException("Fulltext configuration does not support embedded search");
    }

    @Override
    SearchResponse<Document> internalSearch(final ElasticsearchClient client, final String indexName, final EmbeddingSearchRequest embeddingSearchRequest, final boolean includeVectorResponse) throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException("Fulltext configuration does not support embedded search");
    }

    @Override
    SearchResponse<Document> internalSearch(final ElasticsearchClient client, final String indexName, final String textQuery) throws ElasticsearchException, IOException {
        return client.search(s -> s
                        .index(indexName)
                        .query(q -> q
                                .match(m -> m
                                        .field("text")
                                        .query(textQuery)
                                )
                        )
                , Document.class);
    }

    @Override
    SearchResponse<Document> internalSearch(final ElasticsearchClient client, final String indexName, final EmbeddingSearchRequest embeddingSearchRequest, final String textQuery, final boolean includeVectorResponse) throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException("Fulltext configuration does not support embedded search");
    }


}
