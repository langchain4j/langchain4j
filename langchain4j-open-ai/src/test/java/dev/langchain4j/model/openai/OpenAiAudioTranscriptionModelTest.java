package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiAudioTranscriptionModelTest {

    @Test
    void should_not_request_verbose_response_when_timestamp_granularities_are_not_set() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body("""
                        {
                          "text": "Hello world"
                        }
                        """).build());

        OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
                .apiKey("test-key")
                .modelName("whisper-1")
                .httpClientProvider(new MockHttpClientBuilder(mockHttpClient))
                .build();

        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        // when
        AudioTranscriptionResponse response = model.transcribe(request);

        // then
        assertThat(mockHttpClient.request().formDataFieldEntries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(tuple("model", "whisper-1"));

        assertThat(response.text()).isEqualTo("Hello world");
        assertThat(response.segments()).isEmpty();
        assertThat(response.words()).isEmpty();
    }

    @Test
    void should_send_timestamp_granularities_and_map_verbose_response() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body("""
                        {
                          "text": "Hello world",
                          "segments": [
                            {
                              "id": 0,
                              "seek": 0,
                              "start": 0.0,
                              "end": 1.2,
                              "text": "Hello world",
                              "tokens": [1, 2],
                              "temperature": 0.0,
                              "avg_logprob": -0.1,
                              "compression_ratio": 1.0,
                              "no_speech_prob": 0.0
                            }
                          ],
                          "words": [
                            {
                              "word": "Hello",
                              "start": 0.0,
                              "end": 0.5
                            }
                          ]
                        }
                        """).build());

        OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
                .apiKey("test-key")
                .modelName("whisper-1")
                .httpClientProvider(new MockHttpClientBuilder(mockHttpClient))
                .build();

        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .timestampGranularities("word", "segment")
                .build();

        // when
        AudioTranscriptionResponse response = model.transcribe(request);

        // then
        assertThat(mockHttpClient.request().formDataFieldEntries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(
                        tuple("model", "whisper-1"),
                        tuple("response_format", "verbose_json"),
                        tuple("timestamp_granularities[]", "word"),
                        tuple("timestamp_granularities[]", "segment"));

        assertThat(response.text()).isEqualTo("Hello world");
        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).text()).isEqualTo("Hello world");
        assertThat(response.segments().get(0).startTime()).isEqualTo(Duration.ZERO);
        assertThat(response.segments().get(0).endTime()).isEqualTo(Duration.ofMillis(1200));
        assertThat(response.words()).hasSize(1);
        assertThat(response.words().get(0).word()).isEqualTo("Hello");
        assertThat(response.words().get(0).startTime()).isEqualTo(Duration.ZERO);
        assertThat(response.words().get(0).endTime()).isEqualTo(Duration.ofMillis(500));
    }
}
