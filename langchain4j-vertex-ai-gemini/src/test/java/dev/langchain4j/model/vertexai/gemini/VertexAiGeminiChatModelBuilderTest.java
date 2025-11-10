package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

class VertexAiGeminiChatModelBuilderTest {

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
