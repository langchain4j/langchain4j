package dev.langchain4j.model.vertexai;

import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiLanguageModelIT {

    @Test
    void testLanguageModel() {
        VertexAiLanguageModel vertexAiLanguageModel = VertexAiLanguageModel.builder()
                .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .publisher("google")
                .modelName("text-bison@001")
                .temperature(0.2)
                .maxOutputTokens(50)
                .topK(40)
                .topP(0.95)
                .maxRetries(3)
                .build();

        Response<String> response = vertexAiLanguageModel.generate("hi, what is java?");

        assertThat(response.content()).containsIgnoringCase("java");
        System.out.println(response);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(7);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void testGemmaModel() {
        VertexAiLanguageModel vertexAiLanguageModel = VertexAiLanguageModel.builder()
            .endpoint("us-central1-aiplatform.googleapis.com:443")
            .project("1029513523185")
            .location("us-central1")
            .publisher("google")
            .modelName("google_gemma-7b-it-1708804465660")
            .maxRetries(3)
            .build();

        Response<String> response = vertexAiLanguageModel.generate("What's the name of the first cat who stepped on the moon?");

        assertThat(response.content()).containsIgnoringCase("cat");
        System.out.println(response);

//        TokenUsage tokenUsage = response.tokenUsage();
//        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
//        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
//        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(7);

        assertThat(response.finishReason()).isNull();
    }
}