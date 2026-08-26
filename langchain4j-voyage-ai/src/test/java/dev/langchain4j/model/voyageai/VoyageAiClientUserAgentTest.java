package dev.langchain4j.model.voyageai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VoyageAiClientUserAgentTest {

    @Test
    void should_send_user_agent_header() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName("voyage-3")
                .maxRetries(0)
                .build();

        // when
        try {
            model.embed("hello");
        } catch (Exception ignored) {
            // the mock returns no body, so response parsing fails after the request is captured
        }

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("User-Agent", List.of("LangChain4j"));
    }

    @Test
    void custom_header_should_override_default_user_agent() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName("voyage-3")
                .customHeaders(Map.of("User-Agent", "MyApp"))
                .maxRetries(0)
                .build();

        // when
        try {
            model.embed("hello");
        } catch (Exception ignored) {
            // the mock returns no body, so response parsing fails after the request is captured
        }

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("User-Agent", List.of("MyApp"));
    }
}
