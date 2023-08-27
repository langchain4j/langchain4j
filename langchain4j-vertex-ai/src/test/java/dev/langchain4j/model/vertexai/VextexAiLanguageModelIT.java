package dev.langchain4j.model.vertexai;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VextexAiLanguageModelIT {

    @Test
    @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testLanguageModel() {
        VextexAiLanguageModel vextexAiLanguageModel = new VextexAiLanguageModel(
                "us-central1-aiplatform.googleapis.com:443",
                "langchain4j",
                "us-central1",
                "google",
                "text-bison@001",
                0.2,
                50,
                40,
                0.95,
                3);

        String response = vextexAiLanguageModel.process("hi, what is java?");

        assertThat(response).containsIgnoringCase("java");
        System.out.println(response);
    }

    @Test
    @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testLanguageModelUseBuilder() {
        VextexAiLanguageModel vextexAiLanguageModel = VextexAiLanguageModel.builder()
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .project("langchain4j")
                .location("us-central1")
                .publisher("google")
                .modelName("text-bison@001")
                .maxOutputTokens(50)
                .maxRetries(3)
                .build();

        String response = vextexAiLanguageModel.process("hi, what is java?");

        assertThat(response).containsIgnoringCase("java");
        System.out.println(response);
    }

}