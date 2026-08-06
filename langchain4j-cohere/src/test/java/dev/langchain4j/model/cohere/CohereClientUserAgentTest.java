package dev.langchain4j.model.cohere;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class CohereClientUserAgentTest {

    @Test
    void should_send_user_agent_header_on_embed() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        CohereEmbeddingModel model = CohereEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName("embed-english-v3.0")
                .build();

        // when
        try {
            model.embed(TextSegment.from("hello"));
        } catch (Exception ignored) {
            // the mock returns no body, so response parsing fails after the request is captured
        }

        // then
        assertThat(mockHttpClient.requests().get(0).headers()).containsEntry("User-Agent", List.of("LangChain4j"));
    }

    @Test
    void should_send_user_agent_header_on_rerank() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        CohereScoringModel model = CohereScoringModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName("rerank-english-v3.0")
                .maxRetries(0)
                .build();

        // when
        try {
            model.scoreAll(List.of(TextSegment.from("document")), "query");
        } catch (Exception ignored) {
            // the mock returns no body, so response parsing fails after the request is captured
        }

        // then
        assertThat(mockHttpClient.requests().get(0).headers()).containsEntry("User-Agent", List.of("LangChain4j"));
    }
}
