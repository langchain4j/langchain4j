package dev.langchain4j.model.openai.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.openai.internal.audio.texttospeech.OpenAiTextToSpeechRequest;
import dev.langchain4j.model.openai.internal.audio.texttospeech.OpenAiTextToSpeechResponse;
import dev.langchain4j.model.openai.internal.audio.texttospeech.OpenAiVoice;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultOpenAiClientTextToSpeechTest {

    @Test
    void should_send_all_parameters() {
        MockHttpClient httpClient = new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("audio/wav")))
                .body(new byte[] {1, 2, 3})
                .build());

        OpenAiTextToSpeechRequest request = OpenAiTextToSpeechRequest.builder()
                .model("gpt-4o-mini-tts")
                .text("Hello")
                .voice(OpenAiVoice.custom("voice_1234"))
                .instructions("Speak cheerfully")
                .responseFormat("wav")
                .speed(1.5)
                .streamFormat("audio")
                .build();

        OpenAiTextToSpeechResponse response =
                client(httpClient).textToSpeech(request).execute();

        assertThat(httpClient.request().body())
                .contains("\"input\" : \"Hello\"")
                .contains("\"model\" : \"gpt-4o-mini-tts\"")
                .containsPattern("\"voice\" : \\{\\s*\"id\" : \"voice_1234\"\\s*}")
                .contains("\"instructions\" : \"Speak cheerfully\"")
                .contains("\"response_format\" : \"wav\"")
                .contains("\"speed\" : 1.5")
                .contains("\"stream_format\" : \"audio\"");
        assertThat(response.audio()).containsExactly(1, 2, 3);
        assertThat(response.contentType()).isEqualTo("audio/wav");
        assertThat(response.usage()).isNull();
    }

    @Test
    void should_send_built_in_voice_as_string() {
        MockHttpClient httpClient = new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new byte[] {1})
                .build());

        OpenAiTextToSpeechRequest request = OpenAiTextToSpeechRequest.builder()
                .model("tts-1")
                .text("Hello")
                .voice(OpenAiVoice.BuiltIn.ALLOY)
                .build();

        client(httpClient).textToSpeech(request).execute();

        assertThat(httpClient.request().body())
                .contains("\"voice\" : \"alloy\"")
                .doesNotContain("instructions", "response_format", "speed", "stream_format");
    }

    @Test
    void should_aggregate_sse_audio_deltas_and_usage() {
        String chunk1 = Base64.getEncoder().encodeToString(new byte[] {1, 2});
        String chunk2 = Base64.getEncoder().encodeToString(new byte[] {3, 4, 5});
        String body = "data: {\"type\":\"speech.audio.delta\",\"audio\":\"" + chunk1 + "\"}\n\n"
                + "data: {\"type\":\"speech.audio.delta\",\"audio\":\"" + chunk2 + "\"}\n\n"
                + "data: {\"type\":\"speech.audio.done\",\"usage\":"
                + "{\"input_tokens\":14,\"output_tokens\":101,\"total_tokens\":115}}\n\n";
        MockHttpClient httpClient = new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("text/event-stream; charset=utf-8")))
                .body(body.getBytes(UTF_8))
                .build());

        OpenAiTextToSpeechRequest request = OpenAiTextToSpeechRequest.builder()
                .model("gpt-4o-mini-tts")
                .text("Hello")
                .voice(OpenAiVoice.BuiltIn.CORAL)
                .responseFormat("pcm")
                .streamFormat("sse")
                .build();

        OpenAiTextToSpeechResponse response =
                client(httpClient).textToSpeech(request).execute();

        assertThat(response.audio()).containsExactly(1, 2, 3, 4, 5);
        assertThat(response.contentType()).isEqualTo("audio/pcm");
        assertThat(response.usage().inputTokens()).isEqualTo(14);
        assertThat(response.usage().outputTokens()).isEqualTo(101);
        assertThat(response.usage().totalTokens()).isEqualTo(115);
    }

    @Test
    void should_copy_all_fields_from_request() {
        OpenAiTextToSpeechRequest request = OpenAiTextToSpeechRequest.builder()
                .model("gpt-4o-mini-tts")
                .text("Hello")
                .voice(OpenAiVoice.BuiltIn.CORAL)
                .instructions("Speak cheerfully")
                .responseFormat("wav")
                .speed(1.5)
                .streamFormat("audio")
                .build();

        OpenAiTextToSpeechRequest copy =
                OpenAiTextToSpeechRequest.builder().from(request).build();

        assertThat(Json.toJson(copy)).isEqualTo(Json.toJson(request));
    }

    private static OpenAiClient client(MockHttpClient httpClient) {
        return OpenAiClient.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("https://api.openai.com/v1")
                .apiKey("test")
                .build();
    }
}
