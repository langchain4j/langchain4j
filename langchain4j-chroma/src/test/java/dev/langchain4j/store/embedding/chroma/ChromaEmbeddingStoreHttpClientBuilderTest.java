package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.http.client.HttpMethod.DELETE;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChromaEmbeddingStoreHttpClientBuilderTest {

    @Test
    void should_preserve_custom_jdk_http_client_builder() {
        // given
        java.net.http.HttpClient.Builder jdkHttpClientBuilder = java.net.http.HttpClient.newBuilder();
        JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder().httpClientBuilder(jdkHttpClientBuilder);

        // when
        new ChromaHttpClient("http://localhost:8000", Duration.ofSeconds(5), false, false, httpClientBuilder, null);

        // then
        assertThat(httpClientBuilder.httpClientBuilder()).isSameAs(jdkHttpClientBuilder);
    }

    @Test
    void should_use_custom_http_client_builder_and_headers_for_v1() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient();
        TestHttpClientBuilder httpClientBuilder = new TestHttpClientBuilder(httpClient);

        // when
        ChromaEmbeddingStore embeddingStore = ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8000")
                .collectionName("test")
                .httpClientBuilder(httpClientBuilder)
                .customHeaders(Map.of("X-Chroma-Token", "test-token"))
                .build();
        embeddingStore.add("id", Embedding.from(List.of(1.0f)));
        embeddingStore.removeAll();

        // then
        assertThat(httpClient.requests()).hasSize(4);
        assertThat(httpClient.requests()).extracting(HttpRequest::method).containsExactly(GET, POST, DELETE, POST);

        HttpRequest request = httpClient.requests().get(0);
        assertThat(request.url()).isEqualTo("http://localhost:8000/api/v1/collections/test");
        assertThat(httpClient.requests()).allSatisfy(this::assertStaticHeaders);
    }

    @Test
    void should_use_custom_http_client_builder_and_dynamic_headers_for_v2() {
        // given
        AtomicInteger headerCalls = new AtomicInteger();
        CapturingHttpClient httpClient = new CapturingHttpClient();
        TestHttpClientBuilder httpClientBuilder = new TestHttpClientBuilder(httpClient);

        // when
        ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V2)
                .baseUrl("http://localhost:8000")
                .tenantName("default")
                .databaseName("default")
                .collectionName("test")
                .httpClientBuilder(httpClientBuilder)
                .customHeaders(() -> Map.of("Authorization", "Bearer token-" + headerCalls.incrementAndGet()))
                .build();

        // then
        assertThat(httpClient.requests()).isNotEmpty();
        assertThat(headerCalls.get()).isEqualTo(httpClient.requests().size());

        for (int i = 0; i < httpClient.requests().size(); i++) {
            assertThat(httpClient.requests().get(i).headers())
                    .containsEntry("Content-Type", List.of("application/json"))
                    .containsEntry("Authorization", List.of("Bearer token-" + (i + 1)));
        }
    }

    @Test
    void should_handle_null_from_custom_headers_supplier() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient();
        TestHttpClientBuilder httpClientBuilder = new TestHttpClientBuilder(httpClient);

        // when
        ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8000")
                .collectionName("test")
                .httpClientBuilder(httpClientBuilder)
                .customHeaders(() -> null)
                .build();

        // then
        assertThat(httpClient.requests()).hasSize(1);
    }

    private void assertStaticHeaders(HttpRequest request) {
        assertThat(request.headers())
                .containsEntry("Content-Type", List.of("application/json"))
                .containsEntry("X-Chroma-Token", List.of("test-token"));
    }
}
