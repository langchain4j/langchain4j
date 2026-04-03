package dev.langchain4j.model.audio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import org.junit.jupiter.api.Test;

class AudioTranscriptionModelTest {

    static class TestAudioTranscriptionModel implements AudioTranscriptionModel {
        private final String responseText;

        TestAudioTranscriptionModel(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request) {
            return new AudioTranscriptionResponse(responseText);
        }
    }

    @Test
    void should_transcribe_to_text_using_default_method() {
        // given
        String expectedText = "This is the transcribed text";
        TestAudioTranscriptionModel model = new TestAudioTranscriptionModel(expectedText);
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        String result = model.transcribeToText(audio);

        // then
        assertThat(result).isEqualTo(expectedText);
    }

    @Test
    void should_handle_null_response_text_in_default_method() {
        // given
        TestAudioTranscriptionModel model = new TestAudioTranscriptionModel(null);
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        String result = model.transcribeToText(audio);

        // then
        assertThat(result).isNull();
    }

    @Test
    void should_handle_empty_response_text_in_default_method() {
        // given
        TestAudioTranscriptionModel model = new TestAudioTranscriptionModel("");
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        String result = model.transcribeToText(audio);

        // then
        assertThat(result).isEqualTo("");
    }

    @Test
    void should_handle_different_audio_types_in_default_method() {
        // given
        String expectedText = "Transcribed audio";
        TestAudioTranscriptionModel model = new TestAudioTranscriptionModel(expectedText);

        // Test with binary data
        Audio binaryAudio = Audio.builder()
                .binaryData("binary audio data".getBytes())
                .mimeType("audio/wav")
                .build();

        // Test with base64 data
        Audio base64Audio = Audio.builder()
                .base64Data("dGVzdCBhdWRpbw==")
                .mimeType("audio/mp3")
                .build();

        // Test with URL
        Audio urlAudio = Audio.builder()
                .url("https://example.com/audio.wav")
                .mimeType("audio/wav")
                .build();

        // when
        String result1 = model.transcribeToText(binaryAudio);
        String result2 = model.transcribeToText(base64Audio);
        String result3 = model.transcribeToText(urlAudio);

        // then
        assertThat(result1).isEqualTo(expectedText);
        assertThat(result2).isEqualTo(expectedText);
        assertThat(result3).isEqualTo(expectedText);
    }

    @Test
    void should_create_request_with_audio_in_default_method() {
        // given
        String expectedText = "Test transcription";
        Audio audio = Audio.builder()
                .binaryData("test".getBytes())
                .mimeType("audio/wav")
                .build();

        // Create a model that verifies the request structure
        AudioTranscriptionModel model = new AudioTranscriptionModel() {
            @Override
            public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request) {
                assertThat(request.audio()).isEqualTo(audio);
                assertThat(request.prompt()).isNull();
                assertThat(request.language()).isNull();
                assertThat(request.temperature()).isNull();
                return new AudioTranscriptionResponse(expectedText);
            }
        };

        // when
        String result = model.transcribeToText(audio);

        // then
        assertThat(result).isEqualTo(expectedText);
    }

    @Test
    void should_handle_long_transcription_text() {
        // given
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longText.append("This is sentence ").append(i).append(". ");
        }
        String expectedText = longText.toString();

        TestAudioTranscriptionModel model = new TestAudioTranscriptionModel(expectedText);
        Audio audio = Audio.builder()
                .binaryData("long audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        String result = model.transcribeToText(audio);

        // then
        assertThat(result).isEqualTo(expectedText);
        assertThat(result.length()).isGreaterThan(1000);
    }
}
