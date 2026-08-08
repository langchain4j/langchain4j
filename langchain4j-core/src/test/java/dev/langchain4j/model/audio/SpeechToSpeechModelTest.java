package dev.langchain4j.model.audio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import org.junit.jupiter.api.Test;

class SpeechToSpeechModelTest {

    @Test
    void should_generate_speech_using_default_method() {
        // given
        Audio input = Audio.builder()
                .binaryData(new byte[] {1, 2})
                .mimeType("audio/wav")
                .build();
        Audio output = Audio.builder()
                .binaryData(new byte[] {3, 4})
                .mimeType("audio/wav")
                .build();
        SpeechToSpeechModel model = request -> {
            assertThat(request.audio()).isEqualTo(input);
            return SpeechToSpeechResponse.from(output, "Hello!");
        };

        // when
        SpeechToSpeechResponse response = model.generate(input);

        // then
        assertThat(response.audio()).isEqualTo(output);
        assertThat(response.transcript()).isEqualTo("Hello!");
    }

    @Test
    void should_pass_optional_parameters_to_implementation() {
        // given
        Audio input =
                Audio.builder().base64Data("YXVkaW8=").mimeType("audio/wav").build();
        SpeechToSpeechRequest request = SpeechToSpeechRequest.builder(input)
                .instructions("Answer briefly")
                .voice("alloy")
                .build();
        SpeechToSpeechModel model = actualRequest -> {
            assertThat(actualRequest).isSameAs(request);
            assertThat(actualRequest.instructions()).isEqualTo("Answer briefly");
            assertThat(actualRequest.voice()).isEqualTo("alloy");
            return SpeechToSpeechResponse.from(input);
        };

        // when
        SpeechToSpeechResponse response = model.generate(request);

        // then
        assertThat(response.audio()).isEqualTo(input);
        assertThat(response.transcript()).isNull();
    }
}
