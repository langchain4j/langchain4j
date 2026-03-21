package dev.langchain4j.model.audio.speech;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AudioSpeechRequestTest {

    @Test
    void should_create_request_with_input_text_only() {
        // when
        AudioSpeechRequest request =
                AudioSpeechRequest.builder().text("test speech").build();

        // then
        assertThat(request.text()).isEqualTo("test speech");
        assertThat(request.voice()).isNull();
    }

    @Test
    void should_create_request_with_all_parameters() {
        // when
        AudioSpeechRequest request =
                AudioSpeechRequest.builder().text("test speech").voice("ash").build();

        // then
        assertThat(request.text()).isEqualTo("test speech");
        assertThat(request.voice()).isEqualTo("ash");
    }
}
