package dev.langchain4j.store.embedding.chroma;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChromaEmbeddingStoreTest {

    private static final Embedding EMBEDDING_1 = Embedding.from(List.of(1f, 2f, 3f));
    private static final Embedding EMBEDDING_2 = Embedding.from(List.of(4f, 5f, 6f));

    @Test
    void should_throw_when_ids_and_embeddings_have_different_sizes() {
        // given
        RecordingHttpClient httpClient = new RecordingHttpClient();
        ChromaEmbeddingStore store = store(httpClient);

        // when + then
        assertThatThrownBy(() -> store.addAll(List.of("id1"), List.of(EMBEDDING_1, EMBEDDING_2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size is not equal to embeddings size");
    }

    @Test
    void should_throw_when_embeddings_and_text_segments_have_different_sizes() {
        // given
        RecordingHttpClient httpClient = new RecordingHttpClient();
        ChromaEmbeddingStore store = store(httpClient);

        // when + then
        assertThatThrownBy(() -> store.addAll(
                        List.of("id1", "id2"),
                        List.of(EMBEDDING_1, EMBEDDING_2),
                        List.of(TextSegment.from("only one segment"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddings size is not equal to textSegments size");
    }

    @Test
    void should_not_call_server_when_input_is_empty() {
        // given
        RecordingHttpClient httpClient = new RecordingHttpClient();
        ChromaEmbeddingStore store = store(httpClient);
        int requestsAfterInit = httpClient.requests.size();

        // when
        store.addAll(List.of(), List.of(), null);

        // then
        assertThat(httpClient.requests).hasSize(requestsAfterInit);
    }

    private static ChromaEmbeddingStore store(HttpClient httpClient) {
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8000")
                .collectionName("test")
                .httpClientBuilder(new FixedHttpClientBuilder(httpClient))
                .build();
    }

    private record FixedHttpClientBuilder(HttpClient httpClient) implements HttpClientBuilder {

        @Override
        public Duration connectTimeout() {
            return null;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration timeout) {
            return this;
        }

        @Override
        public Duration readTimeout() {
            return null;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration timeout) {
            return this;
        }

        @Override
        public HttpClient build() {
            return httpClient;
        }
    }

    private static class RecordingHttpClient implements HttpClient {

        private final List<HttpRequest> requests = new ArrayList<>();

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            requests.add(request);
            String body = request.url().endsWith("/add")
                    ? "true"
                    : "{\"id\":\"collection-id\",\"name\":\"test\",\"metadata\":{}}";
            return SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .headers(emptyMap())
                    .body(body)
                    .build();
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException("SSE is not used by ChromaEmbeddingStore");
        }
    }
}
