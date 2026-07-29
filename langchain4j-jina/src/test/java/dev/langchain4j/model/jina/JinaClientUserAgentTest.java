package dev.langchain4j.model.jina;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class JinaClientUserAgentTest {

    @Test
    void should_send_user_agent_header_on_embed() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        JinaEmbeddingModel model = JinaEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName("jina-embeddings-v3")
                .maxRetries(0)
                .build();

        // when
        try {
            model.embed("hello");
        } catch (Exception ignored) {
            // the mock returns no body, so response parsing fails after the request is captured
        }

        // then
        assertThat(mockHttpClient.requests().get(0).headers()).containsEntry("User-Agent", List.of("LangChain4j"));
    }
}
