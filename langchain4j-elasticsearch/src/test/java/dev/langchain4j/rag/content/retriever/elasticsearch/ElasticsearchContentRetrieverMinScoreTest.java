package dev.langchain4j.rag.content.retriever.elasticsearch;

import static co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.TransportOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.Document;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ElasticsearchContentRetrieverMinScoreTest {

    private static final double MIN_SCORE = 0.8;

    /**
     * A hit whose score is exactly equal to minScore is on the inclusive boundary.
     * The Elasticsearch query (min_score) is inclusive, so such a hit reaches the Java
     * post-filter. The post-filter must keep it, matching the sibling stores
     * (Milvus/Qdrant/Pinecone/Chroma) which all use {@code >=}.
     */
    @Test
    void should_keep_match_whose_score_equals_min_score() {
        CapturingTransport transport = new CapturingTransport(MIN_SCORE);
        ElasticsearchClient client = new ElasticsearchClient(transport);

        ElasticsearchContentRetriever retriever = ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName("test")
                .embeddingModel(new FixedEmbeddingModel())
                .maxResults(3)
                .minScore(MIN_SCORE)
                .build();

        List<Content> contents = retriever.retrieve(Query.from("any query"));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).textSegment().text()).isEqualTo("hello");
    }

    @Test
    void should_drop_match_whose_score_is_below_min_score() {
        CapturingTransport transport = new CapturingTransport(MIN_SCORE - 0.1);
        ElasticsearchClient client = new ElasticsearchClient(transport);

        ElasticsearchContentRetriever retriever = ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName("test")
                .embeddingModel(new FixedEmbeddingModel())
                .maxResults(3)
                .minScore(MIN_SCORE)
                .build();

        List<Content> contents = retriever.retrieve(Query.from("any query"));

        assertThat(contents).isEmpty();
    }

    private static class FixedEmbeddingModel implements EmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return Response.from(textSegments.stream()
                    .map(ignored -> Embedding.from(new float[] {0.1f, 0.2f}))
                    .toList());
        }
    }

    private static class CapturingTransport implements ElasticsearchTransport {

        private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();
        private final double score;

        private CapturingTransport(double score) {
            this.score = score;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            Hit<Document> hit = Hit.of(h -> h.index("test")
                    .id("1")
                    .score(score)
                    .source(Document.builder()
                            .vector(new float[] {0.1f, 0.2f})
                            .text("hello")
                            .metadata(Map.of())
                            .build()));
            SearchResponse<Document> response = SearchResponse.of(sr -> sr.took(0)
                    .timedOut(false)
                    .shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.total(t -> t.value(1).relation(Eq)).hits(singletonList(hit))));
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
