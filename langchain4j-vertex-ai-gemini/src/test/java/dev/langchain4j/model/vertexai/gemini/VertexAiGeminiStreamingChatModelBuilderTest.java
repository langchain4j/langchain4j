package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VertexAiGeminiStreamingChatModelBuilderTest {

    @Test
    void setCustomHeader() {
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .customHeaders(Map.of("foo", "bar"))
                .build();

        final String actualFooHeader = model.vertexAI().getHeaders().getOrDefault("foo", "error");
        assertThat(actualFooHeader).isEqualTo("bar");
    }

    @Test
    void setDefaultUserAgent() {
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .build();

        final String actualFooHeader = model.vertexAI().getHeaders().getOrDefault("user-agent", "error");
        assertThat(actualFooHeader).contains("LangChain4j");
    }

    @Test
    void overwriteDefaultUserAgent() {
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .customHeaders(Map.of("user-agent", "my-custom-user-agent"))
                .build();

        final String actualFooHeader = model.vertexAI().getHeaders().getOrDefault("user-agent", "error");
        assertThat(actualFooHeader).contains("my-custom-user-agent");
    }

    @Test
    void setCustomApiEndpoint() {
        String customEndpoint = "https://custom-endpoint.example.com";
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .apiEndpoint(customEndpoint)
                .build();

        assertThat(model.vertexAI().getApiEndpoint()).isEqualTo(customEndpoint);
    }

    @Test
    void setLabels() {
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .labels(Map.of("company_id", "20096", "user_id", "12345"))
                .build();

        assertThat(model.labels()).containsEntry("company_id", "20096").containsEntry("user_id", "12345");
    }

    @Test
    void labelsDefaultToEmpty() {
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .build();

        assertThat(model.labels()).isEmpty();
    }

    @Test
    void returnThinking_should_be_disabled_by_default() throws ReflectiveOperationException {
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .build();

        assertThat(fieldValue(model, "returnThinking")).isEqualTo(false);
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
