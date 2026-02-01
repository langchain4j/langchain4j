package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.ScriptScoreQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import java.io.IOException;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 * <br>
 * Supports storing {@link Metadata} and filtering by it using {@link Filter}
 * (provided inside {@link EmbeddingSearchRequest}).
 *
 * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html#vector-functions-cosine">vector-functions-cosine</a>
 */
public class ElasticsearchConfigurationScript extends ElasticsearchConfiguration {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class Builder {
        private boolean includeVectorResponse = false;

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

        public ElasticsearchConfigurationScript build() {
            return new ElasticsearchConfigurationScript(includeVectorResponse);
        }
    }

    public static ElasticsearchConfigurationScript.Builder builder() {
        return new ElasticsearchConfigurationScript.Builder();
    }

    private ElasticsearchConfigurationScript(final boolean includeVectorResponse) {
        this.includeVectorResponse = includeVectorResponse;
    }

    @Override
    SearchResponse<Document> vectorSearch(
            ElasticsearchClient client, String indexName, EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException, IOException {
        ScriptScoreQuery scriptScoreQuery = buildDefaultScriptScoreQuery(
                embeddingSearchRequest.queryEmbedding().vector(),
                (float) embeddingSearchRequest.minScore(),
                embeddingSearchRequest.filter());
        return client.search(
                SearchRequest.of(s -> s.source(sr -> {
                            if (includeVectorResponse) {
                                return sr.filter(f -> f.excludeVectors(false));
                            }
                            return new SourceConfig.Builder().filter(f -> f);
                        })
                        .index(indexName)
                        .query(n -> n.scriptScore(scriptScoreQuery))
                        .size(embeddingSearchRequest.maxResults())),
                Document.class);
    }

    @Override
    SearchResponse<Document> fullTextSearch(
            final ElasticsearchClient client, final String indexName, final String textQuery)
            throws ElasticsearchException {
        throw new UnsupportedOperationException("Script configuration does not support full text search");
    }

    @Override
    SearchResponse<Document> hybridSearch(
            final ElasticsearchClient client,
            final String indexName,
            final EmbeddingSearchRequest embeddingSearchRequest,
            final String textQuery)
            throws ElasticsearchException {
        throw new UnsupportedOperationException("Script configuration does not support hybrid search");
    }

    private ScriptScoreQuery buildDefaultScriptScoreQuery(float[] vector, float minScore, Filter filter)
            throws JsonProcessingException {
        JsonData queryVector = toJsonData(vector);
        Query query;
        if (filter == null) {
            query = Query.of(q -> q.matchAll(m -> m));
        } else {
            query = ElasticsearchMetadataFilterMapper.map(filter);
        }
        return ScriptScoreQuery.of(q -> q.minScore(minScore).query(query).script(s -> s.source(
                        "(cosineSimilarity(params.query_vector, 'vector') + 1.0) / 2")
                .params("query_vector", queryVector)));
    }

    private <T> JsonData toJsonData(T rawData) throws JsonProcessingException {
        return JsonData.fromJson(objectMapper.writeValueAsString(rawData));
    }
}
