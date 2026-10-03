package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaUserAgentTest {

    @Test
    void should_send_default_user_agent_without_custom_headers() {
        MockHttpClient mockHttpClient = new MockHttpClient();

        OllamaChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .maxRetries(0)
                .build();

        // when
        try {
            model.chat("test");
        } catch (Exception ignored) {
        }

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("User-Agent", List.of("LangChain4j"));
    }

    @Test
    void should_send_default_user_agent_alongside_custom_headers() {
        MockHttpClient mockHttpClient = new MockHttpClient();

        OllamaChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .customHeaders(Map.of("X-Custom-Header", "custom-value"))
                .maxRetries(0)
                .build();

        // when
        try {
            model.chat("test");
        } catch (Exception ignored) {
        }

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("User-Agent", List.of("LangChain4j"));
        assertThat(mockHttpClient.request().headers()).containsEntry("X-Custom-Header", List.of("custom-value"));
    }
}
