package dev.langchain4j.model.workersai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class WorkersAiEmbeddingModelListenerTest {

    private static final String ONE_EMBEDDING =
            "{\"success\":true,\"result\":{\"shape\":[1,3],\"data\":[[0.1,0.2,0.3]]}}";

    private final List<Object> notified = new CopyOnWriteArrayList<>();

    private final EmbeddingModelListener listener = new EmbeddingModelListener() {

        @Override
        public void onRequest(EmbeddingModelRequestContext requestContext) {
            notified.add(requestContext);
        }

        @Override
        public void onResponse(EmbeddingModelResponseContext responseContext) {
            notified.add(responseContext);
        }
    };

    @Test
    void should_notify_listeners_when_embedding_a_text() {
        WorkersAiEmbeddingModel model = modelRespondingWith(ONE_EMBEDDING);

        model.embed("hello");

        assertThat(notified)
                .hasExactlyElementsOfTypes(EmbeddingModelRequestContext.class, EmbeddingModelResponseContext.class);
        assertThat(((EmbeddingModelRequestContext) notified.get(0)).textSegments())
                .containsExactly(TextSegment.from("hello"));
    }

    @Test
    void should_notify_listeners_when_embedding_text_segments() {
        WorkersAiEmbeddingModel model = modelRespondingWith(ONE_EMBEDDING);

        model.embedAll(List.of(TextSegment.from("hello")));

        assertThat(notified)
                .hasExactlyElementsOfTypes(EmbeddingModelRequestContext.class, EmbeddingModelResponseContext.class);
    }

    private WorkersAiEmbeddingModel modelRespondingWith(String body) {
        MockHttpClient httpClient = new MockHttpClient(
                SuccessfulHttpResponse.builder().statusCode(200).body(body).build());
        WorkersAiEmbeddingModel.Builder builder = WorkersAiEmbeddingModel.builder()
                .accountId("account")
                .apiToken("token")
                .modelName("@cf/baai/bge-small-en-v1.5")
                .httpClientBuilder(new MockHttpClientBuilder(httpClient));
        return new WorkersAiEmbeddingModel(builder) {

            @Override
            public List<EmbeddingModelListener> listeners() {
                return List.of(listener);
            }
        };
    }
}
