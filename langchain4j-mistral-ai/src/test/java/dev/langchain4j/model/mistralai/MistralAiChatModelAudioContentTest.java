package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.net.URI;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Tests for AudioContent support in Mistral AI chat model.
 *
 * <p>These tests verify that AudioContent (with both URL and base64 audio data) is correctly
 * mapped to Mistral AI's API format for chat completions.
 */
class MistralAiChatModelAudioContentTest {

    private static final String TEST_API_KEY = "test-api-key";

    @Test
    void should_handle_audio_content_with_url() {
        // given
        String jsonResponse =
                """
                {
                    "id": "abc123",
                    "created": 1769421662,
                    "model": "voxtral-mini-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": [
                                {"type": "text", "text": "I can hear the audio clearly."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey(TEST_API_KEY)
                .modelName(MistralAiChatModelName.VOXTRAL_MINI_LATEST)
                .build();

        UserMessage userMessage = UserMessage.from(AudioContent.from(URI.create("https://example.com/audio.wav")));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isEqualTo("I can hear the audio clearly.");

        // verify the request body contains audio URL content (JSON has spaces around colons)
        String requestBody = mockHttpClient.request().body();
        assertThat(requestBody).contains("\"type\" : \"input_audio\"");
        assertThat(requestBody).contains("https://example.com/audio.wav");
    }

    @Test
    void should_handle_audio_content_with_base64() {
        // given
        String jsonResponse =
                """
                {
                    "id": "abc123",
                    "created": 1769421662,
                    "model": "voxtral-mini-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": [
                                {"type": "text", "text": "Audio processed successfully."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey(TEST_API_KEY)
                .modelName(MistralAiChatModelName.VOXTRAL_MINI_LATEST)
                .build();

        // Create a small WAV file header as test data
        String base64Audio = Base64.getEncoder().encodeToString("RIFF".getBytes());
        Audio audio = Audio.builder()
                .base64Data(base64Audio)
                .mimeType("audio/wav")
                .build();
        UserMessage userMessage = UserMessage.from(AudioContent.from(audio));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isEqualTo("Audio processed successfully.");

        // verify the request body contains base64 audio content
        String requestBody = mockHttpClient.request().body();
        assertThat(requestBody).contains("\"type\" : \"input_audio\"");
        assertThat(requestBody).contains("data:audio/wav;base64,");
        assertThat(requestBody).contains(base64Audio);
    }

    @Test
    void should_handle_mixed_text_and_audio_content() {
        // given
        String jsonResponse =
                """
                {
                    "id": "abc123",
                    "created": 1769421662,
                    "model": "voxtral-mini-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": [
                                {"type": "text", "text": "I analyzed the audio you sent."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey(TEST_API_KEY)
                .modelName(MistralAiChatModelName.VOXTRAL_MINI_LATEST)
                .build();

        UserMessage userMessage = UserMessage.from(
                TextContent.from("Please analyze this audio"),
                AudioContent.from(URI.create("https://example.com/audio.mp3")));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isEqualTo("I analyzed the audio you sent.");

        // verify the request body contains both text and audio content
        String requestBody = mockHttpClient.request().body();
        assertThat(requestBody).contains("\"type\" : \"text\"");
        assertThat(requestBody).contains("Please analyze this audio");
        assertThat(requestBody).contains("\"type\" : \"input_audio\"");
        assertThat(requestBody).contains("https://example.com/audio.mp3");
    }

    @Test
    void should_use_voxtral_small_latest_model() {
        // given
        String jsonResponse =
                """
                {
                    "id": "abc123",
                    "created": 1769421662,
                    "model": "voxtral-small-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": [
                                {"type": "text", "text": "Processed with Voxtral small."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey(TEST_API_KEY)
                .modelName(MistralAiChatModelName.VOXTRAL_SMALL_LATEST)
                .build();

        UserMessage userMessage = UserMessage.from(AudioContent.from(URI.create("https://example.com/audio.wav")));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isEqualTo("Processed with Voxtral small.");

        // verify the request uses the correct model
        String requestBody = mockHttpClient.request().body();
        assertThat(requestBody).contains("voxtral-small-latest");
    }
}
