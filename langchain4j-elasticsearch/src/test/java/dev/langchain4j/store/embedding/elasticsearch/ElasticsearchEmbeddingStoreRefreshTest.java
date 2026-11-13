package dev.langchain4j.store.embedding.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.rag.content.retriever.elasticsearch.ElasticsearchContentRetriever;
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicStatusLine;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("removal")
class ElasticsearchEmbeddingStoreRefreshTest {

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_to_vector_and_text_writes(Refresh refresh) throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(client)
                .indexName("refresh-test")
                .refresh(refresh)
                .build();

        writeVectorsAndText(store);

        assertWriteRequests(client, refresh);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_keep_refresh_disabled_by_default(boolean useConstructor) throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchEmbeddingStore store = useConstructor
                ? new ElasticsearchEmbeddingStore(
                        ElasticsearchConfigurationKnn.builder().build(), client, "refresh-test")
                : ElasticsearchEmbeddingStore.builder()
                        .client(client)
                        .indexName("refresh-test")
                        .build();

        writeVectorsAndText(store);

        assertWriteRequests(client, Refresh.False);
    }

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_with_rest_client(Refresh refresh) throws IOException {
        RestClient restClient = mockRestClient();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .restClient(restClient)
                .indexName("refresh-test")
                .refresh(refresh)
                .build();

        writeVectorsAndText(store);

        assertRestRequests(restClient, refresh);
    }

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_with_server_url(Refresh refresh) throws IOException {
        RestClient restClient = mockRestClient();
        RestClientBuilder restClientBuilder = mock(RestClientBuilder.class, RETURNS_SELF);
        when(restClientBuilder.build()).thenReturn(restClient);
        try (var factory = mockStatic(RestClient.class)) {
            factory.when(() -> RestClient.builder(any(HttpHost.class))).thenReturn(restClientBuilder);
            ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                    .serverUrl("http://localhost:9200")
                    .indexName("refresh-test")
                    .refresh(refresh)
                    .build();

            writeVectorsAndText(store);

            assertRestRequests(restClient, refresh);
        }
    }

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_to_removal_by_id(Refresh refresh) throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(client)
                .refresh(refresh)
                .build();

        store.removeAll(List.of("old-chunk"));

        ArgumentCaptor<BulkRequest> request = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client).bulk(request.capture());
        assertThat(request.getValue().refresh()).isEqualTo(refresh);
        assertThat(request.getValue().operations()).singleElement().satisfies(operation -> {
            assertThat(operation.isDelete()).isTrue();
            assertThat(operation.delete().id()).isEqualTo("old-chunk");
        });
    }

    @Test
    void should_fall_back_to_default_when_refresh_is_null() throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(client)
                .indexName("refresh-test")
                .refresh(null)
                .build();

        writeVectorsAndText(store);

        assertWriteRequests(client, Refresh.False);
    }

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_to_content_retriever_writes(Refresh refresh) throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchContentRetriever retriever = ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName("refresh-test")
                .refresh(refresh)
                .build();

        writeVectorsAndText(retriever);

        assertWriteRequests(client, refresh);
    }

    private static ElasticsearchClient mockClient() throws IOException {
        ElasticsearchClient client = mock(ElasticsearchClient.class, RETURNS_SELF);
        when(client.bulk(any(BulkRequest.class)))
                .thenReturn(BulkResponse.of(
                        response -> response.took(0).errors(false).items(List.of())));
        return client;
    }

    private static RestClient mockRestClient() throws IOException {
        RestClient restClient = mock(RestClient.class);
        Response response = mock(Response.class);
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        when(response.getHeader("X-Elastic-Product")).thenReturn("Elasticsearch");
        when(response.getEntity())
                .thenAnswer(ignored ->
                        new StringEntity("{\"took\":0,\"errors\":false,\"items\":[]}", ContentType.APPLICATION_JSON));
        when(restClient.performRequest(any(Request.class))).thenReturn(response);
        return restClient;
    }

    private static void writeVectorsAndText(AbstractElasticsearchEmbeddingStore store) {
        store.addAll(List.of(Embedding.from(new float[] {1, 0}), Embedding.from(new float[] {0, 1})));
        store.addAllText(List.of("first chunk", "second chunk"));
    }

    private static void assertWriteRequests(ElasticsearchClient client, Refresh refresh) throws IOException {
        ArgumentCaptor<BulkRequest> requests = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.refresh()).isEqualTo(refresh);
            assertThat(request.operations()).hasSize(2).allSatisfy(operation -> {
                assertThat(operation.isIndex()).isTrue();
                assertThat(operation.index().index()).isEqualTo("refresh-test");
            });
        });
    }

    private static void assertRestRequests(RestClient restClient, Refresh refresh) throws IOException {
        ArgumentCaptor<Request> requests = ArgumentCaptor.forClass(Request.class);
        verify(restClient, times(2)).performRequest(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.getEndpoint()).isEqualTo("/_bulk");
            assertThat(request.getParameters()).containsEntry("refresh", refresh.jsonValue());
        });
    }
}
