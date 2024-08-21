package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.InlineScript;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.ScriptScoreQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
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
 * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html#vector-functions-cosine">vector-functions-cosine</a>
 */
public class ElasticsearchConfigurationScript extends ElasticsearchConfiguration {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class Builder {
        public ElasticsearchConfigurationScript build() {
            return new ElasticsearchConfigurationScript();
        }
    }

    public static ElasticsearchConfigurationScript.Builder builder() {
        return new ElasticsearchConfigurationScript.Builder();
    }

    private ElasticsearchConfigurationScript() {

    }

    @Override
    SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                                   String indexName,
                                                   EmbeddingSearchRequest embeddingSearchRequest) throws ElasticsearchException, IOException {
        ScriptScoreQuery scriptScoreQuery = buildDefaultScriptScoreQuery(embeddingSearchRequest.queryEmbedding().vector(),
                (float) embeddingSearchRequest.minScore(), embeddingSearchRequest.filter());
        return client.search(
                SearchRequest.of(s -> s.index(indexName)
                        .query(n -> n.scriptScore(scriptScoreQuery))
                        .size(embeddingSearchRequest.maxResults())),
                Document.class
        );
    }

    private ScriptScoreQuery buildDefaultScriptScoreQuery(float[] vector, float minScore,
                                                          Filter filter) throws JsonProcessingException {
        JsonData queryVector = toJsonData(vector);
        Query query;
        if (filter == null) {
            query = Query.of(q -> q.matchAll(m -> m));
        } else {
            query = ElasticsearchMetadataFilterMapper.map(filter);
        }
        return ScriptScoreQuery.of(q -> q
                .minScore(minScore)
                .query(query)
                .script(s -> s.inline(InlineScript.of(i -> i
                        // The script adds 1.0 to the cosine similarity to prevent the score from being negative.
                        // divided by 2 to keep score in the range [0, 1]
                        .source("(cosineSimilarity(params.query_vector, 'vector') + 1.0) / 2")
                        .params("query_vector", queryVector)))));
    }

    private <T> JsonData toJsonData(T rawData) throws JsonProcessingException {
        return JsonData.fromJson(objectMapper.writeValueAsString(rawData));
    }
}
