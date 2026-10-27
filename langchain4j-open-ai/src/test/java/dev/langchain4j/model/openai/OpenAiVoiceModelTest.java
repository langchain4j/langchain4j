package dev.langchain4j.model.openai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiVoiceModelTest {

    private final MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
            {
              "id": "voice_123abc",
              "created_at": 1760000000,
              "name": "My new voice",
              "object": "audio.voice",
              "type": "prompt",
              "prompt": "A warm, calm narrator voice"
            }
            """));

    private final OpenAiVoiceModel model = OpenAiVoiceModel.builder()
            .httpClientBuilder(new MockHttpClientBuilder(httpClient))
            .apiKey("test")
            .build();

    @Test
    void should_create_voice() {
        OpenAiCreateVoiceResponse voice = model.createVoice(OpenAiCreateVoiceRequest.builder()
                .name("My new voice")
                .prompt("A warm, calm narrator voice")
                .scriptHint("Hello, welcome to the show.")
                .build());

        assertThat(voice.id()).isEqualTo("voice_123abc");
        assertThat(httpClient.request().url()).isEqualTo("https://api.openai.com/v1/audio/voices");
    }

    @Test
    void should_reject_null_request() {
        assertThatThrownBy(() -> model.createVoice(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void should_reject_voice_without_name(String name) {
        OpenAiCreateVoiceRequest request = OpenAiCreateVoiceRequest.builder()
                .name(name)
                .prompt("A warm voice")
                .build();

        assertThatThrownBy(() -> model.createVoice(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_name_exceeding_max_length() {
        OpenAiCreateVoiceRequest request = OpenAiCreateVoiceRequest.builder()
                .name("a".repeat(257))
                .prompt("A warm voice")
                .build();

        assertThatThrownBy(() -> model.createVoice(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name exceeds the maximum length of 256 characters");
        assertThat(httpClient.requests()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void should_reject_voice_without_prompt(String prompt) {
        OpenAiCreateVoiceRequest request = OpenAiCreateVoiceRequest.builder()
                .name("My voice")
                .prompt(prompt)
                .build();

        assertThatThrownBy(() -> model.createVoice(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_prompt_exceeding_max_length() {
        OpenAiCreateVoiceRequest request = OpenAiCreateVoiceRequest.builder()
                .name("My voice")
                .prompt("a".repeat(2001))
                .build();

        assertThatThrownBy(() -> model.createVoice(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt exceeds the maximum length of 2000 characters");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_empty_script_hint() {
        OpenAiCreateVoiceRequest request = OpenAiCreateVoiceRequest.builder()
                .name("My voice")
                .prompt("A warm voice")
                .scriptHint("")
                .build();

        assertThatThrownBy(() -> model.createVoice(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Script hint must not be empty");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_script_hint_exceeding_max_length() {
        OpenAiCreateVoiceRequest request = OpenAiCreateVoiceRequest.builder()
                .name("My voice")
                .prompt("A warm voice")
                .scriptHint("a".repeat(2001))
                .build();

        assertThatThrownBy(() -> model.createVoice(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Script hint exceeds the maximum length of 2000 characters");
        assertThat(httpClient.requests()).isEmpty();
    }

    private static SuccessfulHttpResponse jsonResponse(String body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(body.getBytes(UTF_8))
                .build();
    }
}
