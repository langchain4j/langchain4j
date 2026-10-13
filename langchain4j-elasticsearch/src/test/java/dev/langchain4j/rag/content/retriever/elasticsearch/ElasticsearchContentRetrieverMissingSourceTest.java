package dev.langchain4j.rag.content.retriever.elasticsearch;

import static co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq;
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
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.elasticsearch.Document;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Two shapes of document that a legitimate Elasticsearch index can return must not break retrieval:
 *
 * <ul>
 *   <li>a hit with no {@code _source} at all - the document body was excluded from the response, so
 *       {@code Hit.source()} is {@code null};
 *   <li>a document indexed without text through
 *       {@link dev.langchain4j.store.embedding.EmbeddingStore#add(String, Embedding)}, which therefore
 *       carries a vector but no text.
 * </ul>
 */
class ElasticsearchContentRetrieverMissingSourceTest {

    private static final float[] VECTOR = {0.1f, 0.2f};
    private static final double SCORE = 0.9;

    /**
     * {@code toMatches} must never put a {@code null} into the list it returns: its callers immediately
     * dereference every element (for example when logging the matches).
     */
    @Test
    void should_not_return_null_matches_when_a_hit_has_no_source() {
        ElasticsearchContentRetriever retriever = retriever(hitWithoutSource());

        EmbeddingSearchResult<TextSegment> result = retriever.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(VECTOR))
                .maxResults(3)
                .minScore(0.0)
                .build());

        assertThat(result.matches()).doesNotContainNull();
    }

    /** A hit without a document body carries nothing to build {@link Content} from, so it is skipped. */
    @Test
    void should_ignore_hits_without_source_when_retrieving_content() {
        ElasticsearchContentRetriever retriever = retriever(hitWithoutSource());

        assertThat(retriever.retrieve(Query.from("any query"))).isEmpty();
    }

    /** A vector-only document has no text to build {@link Content} from, so it is skipped. */
    @Test
    void should_ignore_hits_without_text_when_retrieving_content() {
        ElasticsearchContentRetriever retriever = retriever(hitWithoutText());

        assertThat(retriever.retrieve(Query.from("any query"))).isEmpty();
    }

    /** A well-formed hit is still returned, so the filtering does not hide valid results. */
    @Test
    void should_return_matches_when_source_and_text_are_present() {
        ElasticsearchContentRetriever retriever = retriever(hitWithText());

        EmbeddingSearchResult<TextSegment> result = retriever.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(VECTOR))
                .maxResults(3)
                .minScore(0.0)
                .build());

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("some text");
        assertThat(retriever.retrieve(Query.from("any query")))
                .extracting(content -> content.textSegment().text())
                .containsExactly("some text");
    }

    /** Malformed hits are skipped without discarding the valid ones next to them. */
    @Test
    void should_keep_valid_hits_when_other_hits_are_malformed() {
        ElasticsearchContentRetriever retriever = retriever(hitWithoutSource(), hitWithoutText(), hitWithText());

        assertThat(retriever.retrieve(Query.from("any query")))
                .extracting(content -> content.textSegment().text())
                .containsExactly("some text");
    }

    private static Hit<Document> hitWithText() {
        return Hit.of(h -> h.index("test")
                .id("3")
                .score(SCORE)
                .source(Document.builder()
                        .vector(VECTOR)
                        .text("some text")
                        .metadata(Map.of())
                        .build()));
    }

    private static Hit<Document> hitWithoutSource() {
        return Hit.of(h -> h.index("test").id("1").score(SCORE));
    }

    private static Hit<Document> hitWithoutText() {
        return Hit.of(h -> h.index("test")
                .id("2")
                .score(SCORE)
                .source(Document.builder().vector(VECTOR).metadata(Map.of()).build()));
    }

    @SafeVarargs
    private static ElasticsearchContentRetriever retriever(Hit<Document>... hits) {
        ElasticsearchClient client = new ElasticsearchClient(new FixedHitsTransport(hits));

        return ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName("test")
                .embeddingModel(new FixedEmbeddingModel())
                .maxResults(3)
                .minScore(0.0)
                .build();
    }

    private static class FixedEmbeddingModel implements EmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return Response.from(
                    textSegments.stream().map(ignored -> Embedding.from(VECTOR)).toList());
        }
    }

    private static class FixedHitsTransport implements ElasticsearchTransport {

        private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();
        private final List<Hit<Document>> hits;

        @SafeVarargs
        private FixedHitsTransport(Hit<Document>... hits) {
            this.hits = List.of(hits);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            SearchResponse<Document> response = SearchResponse.of(sr -> sr.took(0)
                    .timedOut(false)
                    .shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.total(t -> t.value(hits.size()).relation(Eq)).hits(hits)));
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
