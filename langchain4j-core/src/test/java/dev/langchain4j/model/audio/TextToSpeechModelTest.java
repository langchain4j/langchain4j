package dev.langchain4j.model.audio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import org.junit.jupiter.api.Test;

class TextToSpeechModelTest {

    static class TestTextToSpeechModel implements TextToSpeechModel {
        private final TextToSpeechResponse response;

        TestTextToSpeechModel(TextToSpeechResponse response) {
            this.response = response;
        }

        @Override
        public TextToSpeechResponse synthesize(TextToSpeechRequest request) {
            return response;
        }
    }

    private static TextToSpeechResponse responseOf(byte[] data) {
        return TextToSpeechResponse.from(Audio.builder().binaryData(data).build());
    }

    @Test
    void should_generate_speech_using_default_method() {
        // given
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        String text = "This is the input text";
        TestTextToSpeechModel model = new TestTextToSpeechModel(responseOf(expectedSpeech));

        // when
        TextToSpeechResponse result = model.synthesize(text);

        // then
        assertThat(result.audio().binaryData()).isEqualTo(expectedSpeech);
    }

    @Test
    void should_handle_null_response_in_default_method() {
        // given
        TestTextToSpeechModel model = new TestTextToSpeechModel(null);
        TextToSpeechRequest textToSpeechRequest =
                TextToSpeechRequest.builder().text("test speech").build();

        // when
        TextToSpeechResponse result = model.synthesize(textToSpeechRequest);

        // then
        assertThat(result).isNull();
    }

    @Test
    void should_handle_different_voice_in_default_method() {
        // given
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        TestTextToSpeechModel model = new TestTextToSpeechModel(responseOf(expectedSpeech));
        TextToSpeechRequest textToSpeechRequest =
                TextToSpeechRequest.builder().text("test speech").voice("ash").build();

        // when
        TextToSpeechResponse result = model.synthesize(textToSpeechRequest);

        // then
        assertThat(result.audio().binaryData()).isEqualTo(expectedSpeech);
    }

    @Test
    void should_pass_request_through_to_implementation() {
        // given
        String text = "test speech";
        String voice = "ash";
        byte[] expectedSpeech = new byte[] {1, 2, 5, 5, 5};
        TextToSpeechRequest textToSpeechRequest =
                TextToSpeechRequest.builder().text(text).voice(voice).build();

        // Create a model that verifies the request structure
        TextToSpeechModel model = new TextToSpeechModel() {
            @Override
            public TextToSpeechResponse synthesize(TextToSpeechRequest request) {
                assertThat(request.text()).isEqualTo(text);
                assertThat(request.voice()).isEqualTo(voice);
                return responseOf(expectedSpeech);
            }
        };

        // when
        TextToSpeechResponse result = model.synthesize(textToSpeechRequest);

        // then
        assertThat(result.audio().binaryData()).isEqualTo(expectedSpeech);
    }
}
