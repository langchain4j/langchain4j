package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpenAiAudioSpeechModelTest {

    @Test
    void should_reject_text_exceeding_max_length() {
        OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder()
                .apiKey("test")
                .modelName(OpenAiAudioSpeechModelName.TTS_1)
                .build();

        String tooLong = "a".repeat(4097);

        assertThatThrownBy(() -> model.generate(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096");
    }

    @Test
    void should_require_model_name() {
        assertThatThrownBy(() -> OpenAiAudioSpeechModel.builder().apiKey("test").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
    }
}
