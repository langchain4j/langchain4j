package dev.langchain4j.store.embedding.elasticsearch;

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
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 * <br>
 * Supports storing {@link Metadata} and filtering by it using {@link Filter}
 * (provided inside {@link EmbeddingSearchRequest}).
 */
public class ElasticsearchEmbeddingStore extends AbstractElasticsearchEmbeddingStore {
    private final ObjectMapper objectMapper;

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
    public ElasticsearchEmbeddingStore(String serverUrl,
                                       String apiKey,
                                       String userName,
                                       String password,
                                       String indexName) {
        super(serverUrl, apiKey, userName, password, indexName);
        this.objectMapper = new ObjectMapper();
    }

    public ElasticsearchEmbeddingStore(RestClient restClient, String indexName) {
        super(restClient, indexName);
        this.objectMapper = new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractElasticsearchEmbeddingStore.Builder {
        public ElasticsearchEmbeddingStore build() {
            if (restClient != null) {
                return new ElasticsearchEmbeddingStore(restClient, indexName);
            } else {
                return new ElasticsearchEmbeddingStore(serverUrl, apiKey, userName, password, indexName);
            }
        }
    }

    @Override
    public SearchResponse<Document> internalSearch(Embedding referenceEmbedding, int maxResults, double minScore) throws ElasticsearchException, IOException {
        // Use Script Score and cosineSimilarity to calculate
        // see https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html#vector-functions-cosine
        ScriptScoreQuery scriptScoreQuery = buildDefaultScriptScoreQuery(referenceEmbedding.vector(), (float) minScore);
        return client.search(
                SearchRequest.of(s -> s.index(indexName)
                        .query(n -> n.scriptScore(scriptScoreQuery))
                        .size(maxResults)),
                Document.class
        );
    }

    private ScriptScoreQuery buildDefaultScriptScoreQuery(float[] vector, float minScore) throws JsonProcessingException {
        JsonData queryVector = toJsonData(vector);
        return ScriptScoreQuery.of(q -> q
                .minScore(minScore)
                .query(Query.of(qu -> qu.matchAll(m -> m)))
                .script(s -> s.inline(InlineScript.of(i -> i
                        // The script adds 1.0 to the cosine similarity to prevent the score from being negative.
                        // divided by 2 to keep score in the range [0, 1]
                        .source("(cosineSimilarity(params.query_vector, 'vector') + 1.0) / 2")
                        .params("query_vector", queryVector)))));
    }

    private <T> JsonData toJsonData(T rawData) throws JsonProcessingException {
        return JsonData.fromJson(objectMapper.writeValueAsString(rawData));
    }

    protected List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(SearchResponse<Document> response) {
        return response.hits().hits().stream()
                .map(hit -> Optional.ofNullable(hit.source())
                        .map(document -> new EmbeddingMatch<>(
                                hit.score(),
                                hit.id(),
                                new Embedding(document.getVector()),
                                document.getText() == null
                                        ? null
                                        : TextSegment.from(document.getText(), new Metadata(document.getMetadata()))
                        )).orElse(null))
                .collect(toList());
    }
}
