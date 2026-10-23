package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void should_send_request_custom_voice_as_object_with_id() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder().voice(OpenAiTextToSpeechVoiceName.CORAL))
                .synthesize(TextToSpeechRequest.builder()
                        .text("Hello")
                        .voice("voice_1234")
                        .build());

        assertThat(httpClient.request().body()).containsPattern("\"voice\" : \\{\\s*\"id\" : \"voice_1234\"\\s*}");
    }

    @Test
    void should_reject_blank_request_voice() {
        OpenAiTextToSpeechModel model = model(mockHttpClient(), OpenAiTextToSpeechModel.builder());

        assertThatThrownBy(() -> model.synthesize(
                        TextToSpeechRequest.builder().text("Hello").voice(" ").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void should_send_default_built_in_voice_set_by_name() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder().voice("echo")).synthesize("Hello");

        assertThat(httpClient.request().body()).contains("\"voice\" : \"echo\"");
    }

    @Test
    void should_send_default_custom_voice_set_by_id_as_object_with_id() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder().voice("voice_1234")).synthesize("Hello");

        assertThat(httpClient.request().body()).containsPattern("\"voice\" : \\{\\s*\"id\" : \"voice_1234\"\\s*}");
    }

    @Test
    void should_reject_blank_default_voice() {
        assertThatThrownBy(() -> OpenAiTextToSpeechModel.builder().voice(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void should_reject_instructions_exceeding_max_length() {
        assertThatThrownBy(() -> OpenAiTextToSpeechModel.builder()
                        .apiKey("test")
                        .modelName(OpenAiTextToSpeechModelName.GPT_4_O_MINI_TTS)
                        .instructions("a".repeat(4097))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instructions")
                .hasMessageContaining("4096");
    }

    @ParameterizedTest
    @EnumSource(
            value = OpenAiTextToSpeechModelName.class,
            names = {"TTS_1", "TTS_1_HD"})
    void should_reject_instructions_for_models_without_instructions_support(OpenAiTextToSpeechModelName modelName) {
        assertThatThrownBy(() -> OpenAiTextToSpeechModel.builder()
                        .apiKey("test")
                        .modelName(modelName)
                        .instructions("Speak cheerfully")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(modelName.toString())
                .hasMessageContaining("does not support instructions");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.24, 4.01})
    void should_reject_speed_out_of_range(double speed) {
        assertThatThrownBy(() -> OpenAiTextToSpeechModel.builder()
                        .apiKey("test")
                        .modelName(OpenAiTextToSpeechModelName.TTS_1)
                        .speed(speed)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("speed");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 4.0})
    void should_accept_speed_at_bounds(double speed) {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder().speed(speed)).synthesize("Hello");

        assertThat(httpClient.request().body()).contains("\"speed\" : " + speed);
    }

    @Test
    void should_send_optional_parameters() {
        MockHttpClient httpClient = mockHttpClient();

        model(
                        httpClient,
                        OpenAiTextToSpeechModel.builder()
                                .instructions("Speak cheerfully")
                                .responseFormat(OpenAiTextToSpeechResponseFormat.WAV)
                                .speed(1.5))
                .synthesize("Hello");

        assertThat(httpClient.request().body())
                .contains("\"instructions\" : \"Speak cheerfully\"")
                .contains("\"response_format\" : \"wav\"")
                .contains("\"speed\" : 1.5")
                .doesNotContain("stream_format");
    }

    @Test
    void should_not_send_unset_optional_parameters() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder()).synthesize("Hello");

        assertThat(httpClient.request().body())
                .doesNotContain("instructions")
                .doesNotContain("response_format")
                .doesNotContain("speed")
                .doesNotContain("stream_format");
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
                .modelName(OpenAiTextToSpeechModelName.GPT_4_O_MINI_TTS)
                .build();
    }

    @ParameterizedTest
    @EnumSource(OpenAiTextToSpeechVoiceName.class)
    void should_send_request_built_in_voice_as_string(OpenAiTextToSpeechVoiceName voiceName) {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder())
                .synthesize(TextToSpeechRequest.builder()
                        .text("Hello")
                        .voice(voiceName.toString())
                        .build());

        assertThat(httpClient.request().body()).contains("\"voice\" : \"" + voiceName + "\"");
    }

    @Test
    void should_send_request_custom_voice_when_no_default_voice_is_set() {
        MockHttpClient httpClient = mockHttpClient();

        model(httpClient, OpenAiTextToSpeechModel.builder())
                .synthesize(TextToSpeechRequest.builder()
                        .text("Hello")
                        .voice("voice_1234")
                        .build());

        assertThat(httpClient.request().body())
                .containsPattern("\"voice\" : \\{\\s*\"id\" : \"voice_1234\"\\s*}")
                .doesNotContain("alloy");
    }
}
