package dev.langchain4j.model.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.audio.Audio;
import org.junit.jupiter.api.Test;

class AudioTranscriptionRequestTest {

    @Test
    void should_create_request_with_audio_only() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        // then
        assertThat(request.audio()).isEqualTo(audio);
        assertThat(request.prompt()).isNull();
        assertThat(request.language()).isNull();
        assertThat(request.temperature()).isNull();
    }

    @Test
    void should_create_request_with_all_parameters() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();
        String prompt = "Test prompt";
        String language = "en";
        Double temperature = 0.5;

        // when
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .prompt(prompt)
                .language(language)
                .temperature(temperature)
                .build();

        // then
        assertThat(request.audio()).isEqualTo(audio);
        assertThat(request.prompt()).isEqualTo(prompt);
        assertThat(request.language()).isEqualTo(language);
        assertThat(request.temperature()).isEqualTo(temperature);
    }

    @Test
    void should_create_request_with_builder_audio_parameter() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder(audio).prompt("Test prompt").build();

        // then
        assertThat(request.audio()).isEqualTo(audio);
        assertThat(request.prompt()).isEqualTo("Test prompt");
    }

    @Test
    void should_throw_exception_when_audio_is_null() {
        // when & then
        assertThatThrownBy(() -> AudioTranscriptionRequest.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Audio must be provided");
    }

    @Test
    void should_handle_zero_temperature() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .temperature(0.0)
                .build();

        // then
        assertThat(request.temperature()).isEqualTo(0.0);
    }

    @Test
    void should_handle_max_temperature() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .temperature(1.0)
                .build();

        // then
        assertThat(request.temperature()).isEqualTo(1.0);
    }

    @Test
    void should_handle_empty_string_parameters() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .prompt("")
                .language("")
                .build();

        // then
        assertThat(request.prompt()).isEqualTo("");
        assertThat(request.language()).isEqualTo("");
    }

    @Test
    void should_handle_various_language_codes() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        // when
        AudioTranscriptionRequest request1 =
                AudioTranscriptionRequest.builder().audio(audio).language("en").build();

        AudioTranscriptionRequest request2 =
                AudioTranscriptionRequest.builder().audio(audio).language("fr").build();

        AudioTranscriptionRequest request3 = AudioTranscriptionRequest.builder()
                .audio(audio)
                .language("es-ES")
                .build();

        // then
        assertThat(request1.language()).isEqualTo("en");
        assertThat(request2.language()).isEqualTo("fr");
        assertThat(request3.language()).isEqualTo("es-ES");
    }

    @Test
    void should_handle_long_prompt() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();
        String longPrompt = "This is a very long prompt that contains multiple sentences. "
                + "It should be used to guide the transcription process. "
                + "The prompt can contain specific terminology or context.";

        // when
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .prompt(longPrompt)
                .build();

        // then
        assertThat(request.prompt()).isEqualTo(longPrompt);
    }
}
