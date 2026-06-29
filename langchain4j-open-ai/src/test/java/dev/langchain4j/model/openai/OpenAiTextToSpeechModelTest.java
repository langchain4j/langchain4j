package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpenAiTextToSpeechModelTest {

    @Test
    void should_reject_text_exceeding_max_length() {
        OpenAiTextToSpeechModel model = OpenAiTextToSpeechModel.builder()
                .apiKey("test")
                .modelName(OpenAiTextToSpeechModelName.TTS_1)
                .build();

        String tooLong = "a".repeat(4097);

        assertThatThrownBy(() -> model.synthesize(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096");
    }

    @Test
    void should_require_model_name() {
        assertThatThrownBy(() -> OpenAiTextToSpeechModel.builder().apiKey("test").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
    }
}
