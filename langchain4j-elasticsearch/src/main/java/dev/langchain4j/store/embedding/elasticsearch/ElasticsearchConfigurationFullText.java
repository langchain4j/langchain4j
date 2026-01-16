package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as a text store
 * using full text search.
 *
 * @see <a href="https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-match-query">match query</a>
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
    SearchResponse<Document> vectorSearch(
            final ElasticsearchClient client,
            final String indexName,
            final EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException {
        throw new UnsupportedOperationException("Full text configuration does not support embedded search");
    }

    @Override
    SearchResponse<Document> fullTextSearch(
            final ElasticsearchClient client, final String indexName, final String textQuery)
            throws ElasticsearchException, IOException {
        log.trace("Searching for text matches in index [{}] with query [{}].", indexName, textQuery);

        return client.search(
                s -> s.index(indexName)
                        .query(q -> q.match(m -> m.field(TEXT_FIELD).query(textQuery))),
                Document.class);
    }

    @Override
    SearchResponse<Document> hybridSearch(
            final ElasticsearchClient client,
            final String indexName,
            final EmbeddingSearchRequest embeddingSearchRequest,
            final String textQuery)
            throws ElasticsearchException {
        throw new UnsupportedOperationException("Full text configuration does not support embedded search");
    }
}
