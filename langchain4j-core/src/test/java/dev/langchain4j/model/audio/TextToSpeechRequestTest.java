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
    void should_be_equal_when_text_and_voice_are_equal() {
        TextToSpeechRequest request =
                TextToSpeechRequest.builder().text("test speech").voice("ash").build();
        TextToSpeechRequest same =
                TextToSpeechRequest.builder().text("test speech").voice("ash").build();
        TextToSpeechRequest otherVoice =
                TextToSpeechRequest.builder().text("test speech").voice("coral").build();

        assertThat(request).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(otherVoice);
        assertThat(request).hasToString("TextToSpeechRequest { text = \"test speech\", voice = \"ash\" }");
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
