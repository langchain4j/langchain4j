package dev.langchain4j.model.audio.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AudioSpeechModelTest {

    static class TestAudioSpeechModel implements AudioSpeechModel {
        private final byte[] responseSpeech;

        TestAudioSpeechModel(byte[] responseSpeech) {
            this.responseSpeech = responseSpeech;
        }

        @Override
        public byte[] generate(AudioSpeechRequest request) {
            return responseSpeech;
        }
    }

    @Test
    void should_generate_speech_using_default_method() {
        // given
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        String inputText = "This is the input text";
        TestAudioSpeechModel model = new TestAudioSpeechModel(expectedSpeech);

        // when
        byte[] result = model.generate(inputText);

        // then
        assertThat(result).isEqualTo(expectedSpeech);
    }

    @Test
    void should_handle_null_response_text_in_default_method() {
        // given
        TestAudioSpeechModel model = new TestAudioSpeechModel(null);
        AudioSpeechRequest audioSpeechRequest =
                AudioSpeechRequest.builder().text("test speech").build();

        // when
        byte[] result = model.generate(audioSpeechRequest);

        // then
        assertThat(result).isNull();
    }

    @Test
    void should_handle_different_voice_in_default_method() {
        // given
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        TestAudioSpeechModel model = new TestAudioSpeechModel(expectedSpeech);
        AudioSpeechRequest audioSpeechRequest =
                AudioSpeechRequest.builder().text("test speech").voice("ash").build();

        // when
        byte[] result = model.generate(audioSpeechRequest);

        // then
        assertThat(result).isEqualTo(expectedSpeech);
    }

    @Test
    void should_create_request_with_audio_in_default_method() {
        // given
        String inputText = "test speech";
        String voice = "ash";
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        AudioSpeechRequest audioSpeechRequest =
                AudioSpeechRequest.builder().text(inputText).voice(voice).build();

        // Create a model that verifies the request structure
        AudioSpeechModel model = new AudioSpeechModel() {
            @Override
            public byte[] generate(AudioSpeechRequest request) {
                assertThat(request.text()).isEqualTo(inputText);
                assertThat(request.voice()).isEqualTo(voice);
                return expectedSpeech;
            }
        };

        // when
        byte[] result = model.generate(audioSpeechRequest);

        // then
        assertThat(result).isEqualTo(expectedSpeech);
    }

    @Test
    void should_handle_long_transcription_text() {
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            longText.append("This is sentence ").append(i).append(". ");
        }
        String longInputText = longText.toString();
        TestAudioSpeechModel model = new TestAudioSpeechModel(expectedSpeech);

        assertThrows(IllegalStateException.class, () -> model.generate(longInputText));
    }
}
