package dev.langchain4j.rag.content.retriever.elasticsearch;

import static co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
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
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ElasticsearchContentRetrieverFullTextTest {

    @Test
    void should_apply_retrieval_constraints_to_full_text_search() {
        CapturingTransport transport = new CapturingTransport();
        ElasticsearchContentRetriever retriever = ElasticsearchContentRetriever.builder()
                .client(new ElasticsearchClient(transport))
                .indexName("articles")
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .maxResults(7)
                .minScore(0.5)
                .filter(metadataKey("tenant").isEqualTo("acme"))
                .build();

        retriever.retrieve(Query.from("search text"));

        SearchRequest request = transport.capturedRequest;
        assertThat(request.index()).containsExactly("articles");
        assertThat(request.size()).isEqualTo(7);
        assertThat(request.minScore()).isEqualTo(0.5);

        assertThat(request.query().isBool()).isTrue();
        assertThat(request.query().bool().must()).singleElement().satisfies(matchQuery -> {
            assertThat(matchQuery.match().field()).isEqualTo(ElasticsearchConfiguration.TEXT_FIELD);
            assertThat(matchQuery.match().query().stringValue()).isEqualTo("search text");
        });
        assertThat(request.query().bool().filter()).singleElement().satisfies(filterQuery -> {
            assertThat(filterQuery.bool().filter()).singleElement().satisfies(termQuery -> {
                assertThat(termQuery.term().field()).isEqualTo("metadata.tenant.keyword");
                assertThat(termQuery.term().value().anyValue().to(String.class)).isEqualTo("acme");
            });
        });
    }

    @Test
    void should_keep_match_query_when_filter_is_not_configured() {
        CapturingTransport transport = new CapturingTransport();
        ElasticsearchContentRetriever retriever = ElasticsearchContentRetriever.builder()
                .client(new ElasticsearchClient(transport))
                .indexName("articles")
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .build();

        retriever.retrieve(Query.from("search text"));

        SearchRequest request = transport.capturedRequest;
        assertThat(request.size()).isEqualTo(3);
        assertThat(request.minScore()).isEqualTo(0.0);
        assertThat(request.query().isMatch()).isTrue();
        assertThat(request.query().match().field()).isEqualTo(ElasticsearchConfiguration.TEXT_FIELD);
        assertThat(request.query().match().query().stringValue()).isEqualTo("search text");
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
