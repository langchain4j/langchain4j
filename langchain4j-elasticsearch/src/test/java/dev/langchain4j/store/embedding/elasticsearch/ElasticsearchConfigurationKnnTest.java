package dev.langchain4j.store.embedding.elasticsearch;

import static co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ScriptScoreQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.TransportOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ElasticsearchConfigurationKnnTest {

    @Test
    void should_apply_min_score_on_the_normalized_cosine_scale() throws IOException {
        CapturingTransport transport = new CapturingTransport();
        ElasticsearchClient client = new ElasticsearchClient(transport);
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {0.1f, 0.2f}))
                .maxResults(3)
                .minScore(0.5)
                .build();

        ElasticsearchConfigurationKnn.builder().build().vectorSearch(client, "movies", searchRequest);

        SearchRequest request = transport.capturedRequest;
        assertThat(request).isNotNull();
        // minScore must not be compared against the raw kNN score, which is in the [-1;1] cosine scale
        assertThat(request.minScore()).isNull();

        ScriptScoreQuery scriptScoreQuery = request.query().scriptScore();
        assertThat(scriptScoreQuery).isNotNull();
        assertThat(scriptScoreQuery.query().knn()).isNotNull();
        assertThat(scriptScoreQuery.query().knn().field()).isEqualTo(ElasticsearchConfiguration.VECTOR_FIELD);
        // minScore is a relevance score in [0;1], so it is compared against the normalized cosine score
        assertThat(scriptScoreQuery.minScore()).isEqualTo(0.5f);
        assertThat(scriptScoreQuery.script().source().scriptString())
                .isEqualTo("(cosineSimilarity(params.query_vector, 'vector') + 1.0) / 2");

        JsonData queryVector = scriptScoreQuery.script().params().get("query_vector");
        assertThat(queryVector.to(Float[].class, transport.jsonpMapper())).containsExactly(0.1f, 0.2f);
    }

    @Test
    void should_apply_filter_and_num_candidates_to_knn_query() throws IOException {
        CapturingTransport transport = new CapturingTransport();
        ElasticsearchClient client = new ElasticsearchClient(transport);
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {0.1f, 0.2f}))
                .maxResults(3)
                .filter(new IsEqualTo("year", 2023))
                .build();

        ElasticsearchConfigurationKnn.builder().numCandidates(7).build().vectorSearch(client, "movies", searchRequest);

        SearchRequest request = transport.capturedRequest;
        assertThat(request).isNotNull();

        KnnQuery knnQuery = request.query().scriptScore().query().knn();
        assertThat(knnQuery).isNotNull();
        assertThat(knnQuery.numCandidates()).isEqualTo(7);
        assertThat(knnQuery.filter()).hasSize(1);
    }

    @Test
    void should_return_scores_as_they_are_scored_by_the_script_score_query() throws IOException {
        // Simulates the Elasticsearch response of the script_score query, whose score is the
        // cosine similarity normalized to a relevance score in [0;1].
        SearchResponse<Document> response = SearchResponse.of(sr -> sr.took(1)
                .timedOut(false)
                .shards(sh -> sh.total(1).successful(1).failed(0))
                .hits(h -> h.total(t -> t.value(2).relation(TotalHitsRelation.Eq))
                        .hits(List.of(
                                Hit.of(hit -> hit.index("movies")
                                        .id("1")
                                        .score(0.95)
                                        .source(Document.builder()
                                                .vector(new float[] {0.1f, 0.2f})
                                                .text("hello")
                                                .metadata(Map.of("year", 2023))
                                                .build())),
                                Hit.of(hit -> hit.index("movies")
                                        .id("2")
                                        .score(0.6)
                                        .source(Document.builder()
                                                .vector(new float[] {0.1f, 0.2f})
                                                .text("world")
                                                .metadata(Map.of("year", 2023))
                                                .build()))))));

        CapturingTransport transport = new CapturingTransport(response);
        ElasticsearchClient client = new ElasticsearchClient(transport);
        ElasticsearchEmbeddingStore store = new ElasticsearchEmbeddingStore(
                ElasticsearchConfigurationKnn.builder().build(), client, "movies");

        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {0.1f, 0.2f}))
                .maxResults(10)
                .build());

        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches().get(0).score()).isEqualTo(0.95);
        assertThat(result.matches().get(0).embeddingId()).isEqualTo("1");
        assertThat(result.matches().get(1).score()).isEqualTo(0.6);
        assertThat(result.matches().get(1).embeddingId()).isEqualTo("2");
    }

    private static class CapturingTransport implements ElasticsearchTransport {

        private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();
        private final SearchResponse<Document> response;
        private SearchRequest capturedRequest;

        CapturingTransport() {
            this(SearchResponse.of(sr -> sr.took(0)
                    .timedOut(false)
                    .shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.total(t -> t.value(0).relation(Eq)).hits(List.of()))));
        }

        CapturingTransport(SearchResponse<Document> response) {
            this.response = response;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            capturedRequest = (SearchRequest) request;
            return (ResponseT) response;
        }

        @Override
        public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
                RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            return CompletableFuture.completedFuture(performRequest(request, endpoint, options));
        }

        @Override
        public JsonpMapper jsonpMapper() {
            return jsonpMapper;
        }

        @Override
        public TransportOptions options() {
            return DefaultTransportOptions.EMPTY;
        }

        @Override
        public void close() {}
    }
}
