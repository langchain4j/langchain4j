package dev.langchain4j.model.huggingface;

import dev.langchain4j.model.output.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HuggingFaceLanguageModelTest {

    @Test
    public void testWhenNullAccessToken() {
        assertThrows(IllegalArgumentException.class, () ->
                HuggingFaceLanguageModel.builder()
                        .accessToken(null)
                        .modelId("gpt2")
                        .build());
    }

    @Test
    public void testWhenEmptyAccessToken() {
        assertThrows(IllegalArgumentException.class, () ->
                HuggingFaceLanguageModel.builder()
                        .accessToken("")
                        .modelId("gpt2")
                        .build());
    }

    @Test
    public void testProcess() {
        HuggingFaceLanguageModel model = HuggingFaceLanguageModel.builder()
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId("gpt2")
                .build();

        Result<String> result = model.process("What is the capital of Germany?");

        assertThat(result.get()).containsIgnoringCase("Berlin");
        System.out.println(result.get());
    }
}