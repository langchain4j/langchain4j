package dev.langchain4j.model.vertexai;

import dev.langchain4j.model.output.Result;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VextexAiLanguageModelIT {

    @Test
    @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testLanguageModel() {
        VextexAiLanguageModel vextexAiLanguageModel = VextexAiLanguageModel.builder()
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .project("langchain4j")
                .location("us-central1")
                .publisher("google")
                .modelName("text-bison@001")
                .temperature(0.2)
                .maxOutputTokens(50)
                .topK(40)
                .topP(0.95)
                .maxRetries(3)
                .build();

        Result<String> result = vextexAiLanguageModel.generate("hi, what is java?");

        assertThat(result.get()).containsIgnoringCase("java");
        System.out.println(result.get());

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(7);

        assertThat(result.finishReason()).isNull();
    }
}