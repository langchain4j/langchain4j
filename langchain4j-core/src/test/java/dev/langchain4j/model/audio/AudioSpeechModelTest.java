package dev.langchain4j.model.audio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import org.junit.jupiter.api.Test;

class AudioSpeechModelTest {

    static class TestAudioSpeechModel implements AudioSpeechModel {
        private final AudioSpeechResponse response;

        TestAudioSpeechModel(AudioSpeechResponse response) {
            this.response = response;
        }

        @Override
        public AudioSpeechResponse generate(AudioSpeechRequest request) {
            return response;
        }
    }

    private static AudioSpeechResponse responseOf(byte[] data) {
        return AudioSpeechResponse.from(Audio.builder().binaryData(data).build());
    }

    @Test
    void should_generate_speech_using_default_method() {
        // given
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        String inputText = "This is the input text";
        TestAudioSpeechModel model = new TestAudioSpeechModel(responseOf(expectedSpeech));

        // when
        AudioSpeechResponse result = model.generate(inputText);

        // then
        assertThat(result.audio().binaryData()).isEqualTo(expectedSpeech);
    }

    @Test
    void should_handle_null_response_in_default_method() {
        // given
        TestAudioSpeechModel model = new TestAudioSpeechModel(null);
        AudioSpeechRequest audioSpeechRequest =
                AudioSpeechRequest.builder().text("test speech").build();

        // when
        AudioSpeechResponse result = model.generate(audioSpeechRequest);

        // then
        assertThat(result).isNull();
    }

    @Test
    void should_handle_different_voice_in_default_method() {
        // given
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        TestAudioSpeechModel model = new TestAudioSpeechModel(responseOf(expectedSpeech));
        AudioSpeechRequest audioSpeechRequest =
                AudioSpeechRequest.builder().text("test speech").voice("ash").build();

        // when
        AudioSpeechResponse result = model.generate(audioSpeechRequest);

        // then
        assertThat(result.audio().binaryData()).isEqualTo(expectedSpeech);
    }

    @Test
    void should_pass_request_through_to_implementation() {
        // given
        String inputText = "test speech";
        String voice = "ash";
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        AudioSpeechRequest audioSpeechRequest =
                AudioSpeechRequest.builder().text(inputText).voice(voice).build();

        // Create a model that verifies the request structure
        AudioSpeechModel model = new AudioSpeechModel() {
            @Override
            public AudioSpeechResponse generate(AudioSpeechRequest request) {
                assertThat(request.text()).isEqualTo(inputText);
                assertThat(request.voice()).isEqualTo(voice);
                return responseOf(expectedSpeech);
            }
        };

        // when
        AudioSpeechResponse result = model.generate(audioSpeechRequest);

        // then
        assertThat(result.audio().binaryData()).isEqualTo(expectedSpeech);
    }
}
