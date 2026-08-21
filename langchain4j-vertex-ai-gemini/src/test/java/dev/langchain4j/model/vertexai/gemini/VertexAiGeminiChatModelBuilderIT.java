package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentRequest;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiGeminiChatModelBuilderIT {

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

    @Test
    void setLabels() {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .labels(Map.of("company_id", "20096", "user_id", "12345"))
                .build();

        assertThat(model.labels()).containsEntry("company_id", "20096").containsEntry("user_id", "12345");
    }

    @Test
    void labelsDefaultToEmpty() {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .build();

        assertThat(model.labels()).isEmpty();
    }

    @Test
    void buildGenerateContentRequestAttachesLabels() {
        VertexAI vertexAI = newTestVertexAI();
        try {
            GenerativeModel model = new GenerativeModel("gemini-2.5-flash", vertexAI);
            List<Content> contents = List.of(Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText("hello").build())
                    .build());

            GenerateContentRequest request = VertexAiGeminiChatModel.buildGenerateContentRequest(
                    model, vertexAI, contents, Map.of("company_id", "20096", "user_id", "12345"));

            assertThat(request.getLabelsMap())
                    .containsEntry("company_id", "20096")
                    .containsEntry("user_id", "12345");
        } finally {
            vertexAI.close();
        }
    }

    @Test
    void buildGenerateContentRequestNoLabelsWhenEmpty() {
        VertexAI vertexAI = newTestVertexAI();
        try {
            GenerativeModel model = new GenerativeModel("gemini-2.5-flash", vertexAI);
            List<Content> contents = List.of(Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText("hello").build())
                    .build());

            GenerateContentRequest request =
                    VertexAiGeminiChatModel.buildGenerateContentRequest(model, vertexAI, contents, Map.of());

            assertThat(request.getLabelsMap()).isEmpty();
        } finally {
            vertexAI.close();
        }
    }

    @Test
    void buildResourceNamePassesThroughFullyQualifiedName() {
        VertexAI vertexAI = newTestVertexAI();
        try {
            String alreadyQualified =
                    "projects/other-project/locations/europe-west4/publishers/google/models/gemini-2.5-flash";
            assertThat(VertexAiGeminiChatModel.buildResourceName(alreadyQualified, vertexAI))
                    .isEqualTo(alreadyQualified);
        } finally {
            vertexAI.close();
        }
    }

    @Test
    void buildResourceNameQualifiesPublishersPrefix() {
        VertexAI vertexAI = newTestVertexAI();
        try {
            assertThat(VertexAiGeminiChatModel.buildResourceName("publishers/google/models/gemini-2.5-flash", vertexAI))
                    .isEqualTo("projects/test-project/locations/us-central1/publishers/google/models/gemini-2.5-flash");
        } finally {
            vertexAI.close();
        }
    }

    @Test
    void buildResourceNameQualifiesModelsPrefix() {
        VertexAI vertexAI = newTestVertexAI();
        try {
            assertThat(VertexAiGeminiChatModel.buildResourceName("models/gemini-2.5-flash", vertexAI))
                    .isEqualTo("projects/test-project/locations/us-central1/publishers/google/models/gemini-2.5-flash");
        } finally {
            vertexAI.close();
        }
    }

    @Test
    void buildResourceNameQualifiesBareModelName() {
        VertexAI vertexAI = newTestVertexAI();
        try {
            assertThat(VertexAiGeminiChatModel.buildResourceName("gemini-2.5-flash", vertexAI))
                    .isEqualTo("projects/test-project/locations/us-central1/publishers/google/models/gemini-2.5-flash");
        } finally {
            vertexAI.close();
        }
    }

    private static VertexAI newTestVertexAI() {
        return new VertexAI.Builder()
                .setProjectId("test-project")
                .setLocation("us-central1")
                .build();
    }
}
