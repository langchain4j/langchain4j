package dev.langchain4j.model.jina;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class JinaTokenUsageTest {

    private static final String DATA = "\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.1,0.2]}]";

    @Test
    void uses_prompt_tokens_when_the_response_reports_them() {
        TokenUsage tokenUsage = embedWith("\"usage\":{\"prompt_tokens\":3,\"total_tokens\":5}");

        assertThat(tokenUsage.inputTokenCount()).isEqualTo(3);
        assertThat(tokenUsage.outputTokenCount()).isZero();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(5);
    }

    @Test
    void falls_back_to_total_tokens_when_prompt_tokens_are_missing() {
        // jina-embeddings-v3 reports only total_tokens, and for an embedding call the two are the same
        TokenUsage tokenUsage = embedWith("\"usage\":{\"total_tokens\":5}");

        assertThat(tokenUsage.inputTokenCount()).isEqualTo(5);
        assertThat(tokenUsage.outputTokenCount()).isZero();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(5);
    }

    @Test
    void has_no_token_usage_when_the_response_reports_none() {
        assertThat(embedWith(null)).isNull();
    }

    private static TokenUsage embedWith(String usageJson) {
        String body = "{\"model\":\"jina-embeddings-v3\"," + (usageJson == null ? "" : usageJson + ",") + DATA + "}";
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(emptyMap())
                .body(body)
                .build());

        return JinaEmbeddingModel.builder()
                .apiKey("test-key")
                .modelName("jina-embeddings-v3")
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .build()
                .embed(EmbeddingRequest.builder().input("hello world").build())
                .metadata()
                .tokenUsage();
    }
}
