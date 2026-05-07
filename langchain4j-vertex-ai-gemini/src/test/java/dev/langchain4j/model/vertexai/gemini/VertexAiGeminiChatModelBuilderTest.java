package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VertexAiGeminiChatModelBuilderTest {

    @Test
    void setCustomHeader() {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .customHeaders(Map.of("X-Test", "value"))
                .build();

        assertThat(model.vertexAI().getHeaders().get("X-Test")).isEqualTo("value");
        assertThat(model.vertexAI().getHeaders().getOrDefault("user-agent", "error"))
                .contains("LangChain4j");
    }

    @Test
    void overwriteDefaultUserAgent() {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .customHeaders(Map.of("user-agent", "my-custom-user-agent"))
                .build();

        assertThat(model.vertexAI().getHeaders().get("user-agent")).contains("my-custom-user-agent");
    }

    @Test
    void setDefaultUserAgent() {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .build();

        final String actualUserAgent = model.vertexAI().getHeaders().getOrDefault("user-agent", "error");
        assertThat(actualUserAgent).contains("LangChain4j");
    }

    @Test
    void setCustomApiEndpoint() {
        String customEndpoint = "https://custom-endpoint.example.com";
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .apiEndpoint(customEndpoint)
                .build();

        assertThat(model.vertexAI().getApiEndpoint()).isEqualTo(customEndpoint);
    }
}
