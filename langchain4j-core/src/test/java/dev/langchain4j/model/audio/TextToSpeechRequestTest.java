package dev.langchain4j.model.audio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextToSpeechRequestTest {

    @Test
    void should_create_request_with_input_text_only() {
        // when
        TextToSpeechRequest request =
                TextToSpeechRequest.builder().text("test speech").build();

        // then
        assertThat(request.text()).isEqualTo("test speech");
        assertThat(request.voice()).isNull();
    }

    @Test
    void should_create_request_with_all_parameters() {
        // when
        TextToSpeechRequest request =
                TextToSpeechRequest.builder().text("test speech").voice("ash").build();

        // then
        assertThat(request.text()).isEqualTo("test speech");
        assertThat(request.voice()).isEqualTo("ash");
    }

    @Test
    void should_not_enforce_provider_specific_length_limit() {
        // Length limits are provider-specific and enforced by the provider, not by the core request.
        String longText = "a".repeat(10_000);

        TextToSpeechRequest request =
                TextToSpeechRequest.builder().text(longText).build();

        assertThat(request.text()).hasSize(10_000);
    }
}
