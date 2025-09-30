package dev.langchain4j.model.audio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AudioTranscriptionResponseTest {

    @Test
    void should_create_response_with_text() {
        // given
        String text = "This is the transcribed text.";

        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(text);

        // then
        assertThat(response.text()).isEqualTo(text);
    }

    @Test
    void should_create_response_with_from_method() {
        // given
        String text = "This is the transcribed text.";

        // when
        AudioTranscriptionResponse response = AudioTranscriptionResponse.from(text);

        // then
        assertThat(response.text()).isEqualTo(text);
    }

    @Test
    void should_handle_null_text() {
        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(null);

        // then
        assertThat(response.text()).isNull();
    }

    @Test
    void should_handle_empty_text() {
        // given
        String text = "";

        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(text);

        // then
        assertThat(response.text()).isEqualTo("");
    }

    @Test
    void should_handle_multiline_text() {
        // given
        String text = "Line 1\nLine 2\nLine 3";

        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(text);

        // then
        assertThat(response.text()).isEqualTo(text);
        assertThat(response.text()).contains("Line 1");
        assertThat(response.text()).contains("Line 2");
        assertThat(response.text()).contains("Line 3");
    }

    @Test
    void should_handle_text_with_special_characters() {
        // given
        String text = "Hello! This is a test with special characters: äöü, éèê, ñ, and emojis 🎵🎤";

        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(text);

        // then
        assertThat(response.text()).isEqualTo(text);
    }

    @Test
    void should_handle_very_long_text() {
        // given
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("This is sentence number ").append(i).append(". ");
        }

        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(longText.toString());

        // then
        assertThat(response.text()).isEqualTo(longText.toString());
        assertThat(response.text().length()).isGreaterThan(10000);
    }

    @Test
    void should_handle_text_with_whitespace() {
        // given
        String text = "  Text with leading and trailing spaces  ";

        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(text);

        // then
        assertThat(response.text()).isEqualTo(text);
    }

    @Test
    void should_handle_text_with_numbers_and_punctuation() {
        // given
        String text = "The year was 2023, and the temperature was 25.5°C. What a day!";

        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(text);

        // then
        assertThat(response.text()).isEqualTo(text);
    }

    @Test
    void should_create_equal_responses_for_same_text() {
        // given
        String text = "Same text";
        AudioTranscriptionResponse response1 = new AudioTranscriptionResponse(text);
        AudioTranscriptionResponse response2 = AudioTranscriptionResponse.from(text);

        // then
        assertThat(response1.text()).isEqualTo(response2.text());
    }

    @Test
    void should_handle_json_like_text() {
        // given
        String text = "{\"transcription\": \"This is a test\", \"confidence\": 0.95}";

        // when
        AudioTranscriptionResponse response = new AudioTranscriptionResponse(text);

        // then
        assertThat(response.text()).isEqualTo(text);
    }
}
