package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import org.junit.jupiter.api.Test;

class OpenAiTextToSpeechModelTest {

    @Test
    void should_reject_text_exceeding_max_length() {
        OpenAiTextToSpeechModel model = OpenAiTextToSpeechModel.builder()
                .apiKey("test")
                .modelName(OpenAiTextToSpeechModelName.TTS_1)
                .build();

        String tooLong = "a".repeat(4097);

        assertThatThrownBy(() -> model.synthesize(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4096");
    }

    @Test
    void should_require_model_name() {
        assertThatThrownBy(
                        () -> OpenAiTextToSpeechModel.builder().apiKey("test").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
    }

    @Test
    void should_send_alloy_by_default() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder()).synthesize("Hello");

        assertThat(httpClient.request().body()).contains("\"voice\" : \"alloy\"");
    }

    @Test
    void should_send_built_in_voice_as_string() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder().voice(OpenAiTextToSpeechVoiceName.CORAL))
                .synthesize("Hello");

        assertThat(httpClient.request().body()).contains("\"voice\" : \"coral\"");
    }

    @Test
    void should_send_custom_voice_as_object_with_id() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder().voice(OpenAiCustomVoice.from("voice_1234")))
                .synthesize("Hello");

        assertThat(httpClient.request().body()).containsPattern("\"voice\" : \\{\\s*\"id\" : \"voice_1234\"\\s*}");
    }

    @Test
    void should_prefer_request_voice_over_default_voice() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder().voice(OpenAiCustomVoice.from("voice_1234")))
                .synthesize(TextToSpeechRequest.builder()
                        .text("Hello")
                        .voice("echo")
                        .build());

        assertThat(httpClient.request().body()).contains("\"voice\" : \"echo\"");
    }

    @Test
    void should_reject_unknown_built_in_voice() {
        assertThatThrownBy(() -> OpenAiTextToSpeechModel.builder().voice("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown built-in voice 'unknown'")
                .hasMessageContaining("OpenAiCustomVoice");
    }

    private static MockHttpClient mockHttpClient() {
        return new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new byte[] {1})
                .build());
    }

    private static OpenAiTextToSpeechModel model(MockHttpClient httpClient, OpenAiTextToSpeechModel.Builder builder) {
        return builder.httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test")
                .modelName(OpenAiTextToSpeechModelName.TTS_1)
                .build();
    }
}
