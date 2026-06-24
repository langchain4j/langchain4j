package dev.langchain4j.store.embedding.elasticsearch;

import static co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.TransportOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ElasticsearchConfigurationHybridTest {

    @Test
    void should_apply_filter_to_standard_and_knn_retrievers() throws IOException {
        CapturingTransport transport = new CapturingTransport();
        ElasticsearchClient client = new ElasticsearchClient(transport);
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {0.1f, 0.2f}))
                .maxResults(3)
                .filter(new IsEqualTo("year", 2023))
                .build();

        ElasticsearchConfigurationHybrid.builder()
                .build()
                .hybridSearch(client, "movies", searchRequest, "movies about space battles");

        assertThat(transport.capturedRequest).isNotNull();
        assertThat(transport.capturedRequest.retriever().rrf().retrievers()).hasSize(2);

        var standardRetriever = transport
                .capturedRequest
                .retriever()
                .rrf()
                .retrievers()
                .get(0)
                .retriever()
                .standard();
        assertThat(standardRetriever.query().match().field()).isEqualTo(ElasticsearchConfiguration.TEXT_FIELD);
        assertThat(standardRetriever.query().match().query().stringValue()).isEqualTo("movies about space battles");
        assertThat(standardRetriever.filter()).hasSize(1);

        var knnRetriever = transport
                .capturedRequest
                .retriever()
                .rrf()
                .retrievers()
                .get(1)
                .retriever()
                .knn();
        assertThat(knnRetriever.filter()).hasSize(1);
        assertThat(knnRetriever.filter().get(0))
                .isEqualTo(standardRetriever.filter().get(0));
    }

    private static class CapturingTransport implements ElasticsearchTransport {

        private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();
        private SearchRequest capturedRequest;

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            capturedRequest = (SearchRequest) request;
            return (ResponseT) SearchResponse.of(sr -> sr.took(0)
                    .timedOut(false)
                    .shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.total(t -> t.value(0).relation(Eq)).hits(emptyList())));
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
