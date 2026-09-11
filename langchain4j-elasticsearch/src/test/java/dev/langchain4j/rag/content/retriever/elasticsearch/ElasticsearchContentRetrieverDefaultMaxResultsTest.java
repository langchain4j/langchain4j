package dev.langchain4j.rag.content.retriever.elasticsearch;

import static co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
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
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.Document;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ElasticsearchContentRetrieverDefaultMaxResultsTest {

    /**
     * The documentation states that {@code maxResults} defaults to 3 and that a builder without it
     * is equivalent to one calling {@code maxResults(3)}. Leaving the field at the {@code int}
     * default of 0 instead made that documented call fail, because
     * {@code EmbeddingSearchRequest} rejects a non-positive {@code maxResults}.
     */
    @Test
    void should_default_max_results_to_three_when_not_set() {
        CapturingTransport transport = new CapturingTransport();
        ElasticsearchClient client = new ElasticsearchClient(transport);

        ElasticsearchContentRetriever retriever = ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName("test")
                .embeddingModel(new FixedEmbeddingModel())
                .build();

        retriever.retrieve(Query.from("any query"));

        assertThat(transport.capturedRequest.size()).isEqualTo(3);
    }

    @Test
    void should_use_max_results_when_set() {
        CapturingTransport transport = new CapturingTransport();
        ElasticsearchClient client = new ElasticsearchClient(transport);

        ElasticsearchContentRetriever retriever = ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName("test")
                .embeddingModel(new FixedEmbeddingModel())
                .maxResults(7)
                .build();

        retriever.retrieve(Query.from("any query"));

        assertThat(transport.capturedRequest.size()).isEqualTo(7);
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
        private SearchRequest capturedRequest;

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            capturedRequest = (SearchRequest) request;
            Hit<Document> hit = Hit.of(h -> h.index("test")
                    .id("1")
                    .score(1.0)
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
