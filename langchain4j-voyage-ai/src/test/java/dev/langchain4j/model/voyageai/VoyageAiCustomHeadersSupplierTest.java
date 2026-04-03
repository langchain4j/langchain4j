package dev.langchain4j.model.voyageai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class VoyageAiCustomHeadersSupplierTest {

    @Test
    void should_invoke_supplier_and_propagate_headers() {
        // given
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<Map<String, String>> headerSupplier = () -> {
            callCount.incrementAndGet();
            return Map.of("X-Custom-Token", "token-" + callCount.get());
        };

        MockHttpClient mockHttpClient = new MockHttpClient();

        VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName("voyage-3")
                .customHeaders(headerSupplier)
                .maxRetries(0)
                .build();

        // when
        try {
            model.embed("test");
        } catch (Exception ignored) {
        }

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("X-Custom-Token", List.of("token-1"));
    }

    @Test
    void should_call_supplier_for_each_request() {
        // given
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<Map<String, String>> headerSupplier = () -> {
            callCount.incrementAndGet();
            return Map.of("X-Custom-Token", "token-" + callCount.get());
        };

        MockHttpClient mockHttpClient = new MockHttpClient();

        VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName("voyage-3")
                .customHeaders(headerSupplier)
                .maxRetries(0)
                .build();

        // when
        try {
            model.embed("first");
        } catch (Exception ignored) {
        }

        try {
            model.embed("second");
        } catch (Exception ignored) {
        }

        // then
        assertThat(mockHttpClient.requests().get(0).headers()).containsEntry("X-Custom-Token", List.of("token-1"));
        assertThat(mockHttpClient.requests().get(1).headers()).containsEntry("X-Custom-Token", List.of("token-2"));
    }

    @Test
    void should_handle_null_from_supplier() {
        // given
        Supplier<Map<String, String>> nullSupplier = () -> null;

        MockHttpClient mockHttpClient = new MockHttpClient();

        VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName("voyage-3")
                .customHeaders(nullSupplier)
                .maxRetries(0)
                .build();

        // when
        try {
            model.embed("test");
        } catch (Exception ignored) {
        }

        // then
        assertThat(mockHttpClient.requests()).hasSize(1);
    }

    @Test
    void should_support_static_map_for_backward_compatibility() {
        // given
        Map<String, String> staticHeaders = Map.of("X-Static", "value");

        MockHttpClient mockHttpClient = new MockHttpClient();

        VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName("voyage-3")
                .customHeaders(staticHeaders)
                .maxRetries(0)
                .build();

        // when
        try {
            model.embed("test");
        } catch (Exception ignored) {
        }

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("X-Static", List.of("value"));
    }
}
