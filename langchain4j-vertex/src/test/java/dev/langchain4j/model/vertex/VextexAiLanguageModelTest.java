package dev.langchain4j.model.vertex;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VextexAiLanguageModelTest {

    @Test
    void testLanguageModel() {
        VextexAiLanguageModel vextexAiLanguageModel = new VextexAiLanguageModel(
                "text-bison@001",
                "langchain4j",
                "us-central1",
                "google",
                "us-central1-aiplatform.googleapis.com:443",
                0.2,
                50,
                40,
                0.95);

        String response = vextexAiLanguageModel.process("hi, what is java?");

        assertThat(response).containsIgnoringCase("java");
        System.out.println(response);
    }

}