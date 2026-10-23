package dev.langchain4j.model.openai;

import static dev.langchain4j.http.client.HttpMethod.DELETE;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.openai.internal.audio.transcription.AudioFile;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceConsentRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiDeleteVoiceConsentResponse;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiListVoiceConsentsRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiUpdateVoiceConsentRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentId;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentListResponse;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiVoiceConsentModelTest {

    private static final byte[] WAV_BYTES = {1, 2, 3, 4};

    private final MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
            {
              "id": "cons_123abc",
              "object": "audio.voice_consent",
              "name": "test_consent",
              "language": "en-US",
              "created_at": 1760000000
            }
            """));

    private final OpenAiVoiceConsentModel model = OpenAiVoiceConsentModel.builder()
            .httpClientBuilder(new MockHttpClientBuilder(httpClient))
            .apiKey("test")
            .build();

    @Test
    void should_create_voice_consent() {
        OpenAiVoiceConsentResponse consent = model.createVoiceConsent(OpenAiCreateVoiceConsentRequest.builder()
                .name("test_consent")
                .language("en-US")
                .recording(wavFile(WAV_BYTES))
                .build());

        assertThat(consent.id()).isEqualTo("cons_123abc");
        assertThat(httpClient.request().url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents");
    }

    @Test
    void should_reject_null_create_voice_consent_request() {
        assertThatThrownBy(() -> model.createVoiceConsent(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void should_reject_voice_consent_without_name(String name) {
        OpenAiCreateVoiceConsentRequest request = OpenAiCreateVoiceConsentRequest.builder()
                .name(name)
                .language("en-US")
                .recording(wavFile(WAV_BYTES))
                .build();

        assertThatThrownBy(() -> model.createVoiceConsent(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void should_reject_voice_consent_without_language(String language) {
        OpenAiCreateVoiceConsentRequest request = OpenAiCreateVoiceConsentRequest.builder()
                .name("test_consent")
                .language(language)
                .recording(wavFile(WAV_BYTES))
                .build();

        assertThatThrownBy(() -> model.createVoiceConsent(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Language is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_voice_consent_without_recording() {
        OpenAiCreateVoiceConsentRequest request = OpenAiCreateVoiceConsentRequest.builder()
                .name("test_consent")
                .language("en-US")
                .build();

        assertThatThrownBy(() -> model.createVoiceConsent(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Recording is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_voice_consent_recording_exceeding_max_size() {
        OpenAiCreateVoiceConsentRequest request = OpenAiCreateVoiceConsentRequest.builder()
                .name("test_consent")
                .language("en-US")
                .recording(wavFile(new byte[10 * 1024 * 1024 + 1]))
                .build();

        assertThatThrownBy(() -> model.createVoiceConsent(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10485760 bytes");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_null_list_voice_consents_request() {
        assertThatThrownBy(() -> model.listVoiceConsents(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void should_reject_list_voice_consents_with_invalid_limit(int limit) {
        OpenAiListVoiceConsentsRequest request =
                OpenAiListVoiceConsentsRequest.builder().limit(limit).build();

        assertThatThrownBy(() -> model.listVoiceConsents(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be between 1 and 100, but is " + limit);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_retrieve_voice_consent_without_consent_id() {
        assertThatThrownBy(() -> model.retrieveVoiceConsent(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request and consent ID are required");
        assertThatThrownBy(() -> model.retrieveVoiceConsent(
                        OpenAiVoiceConsentId.builder().consentId(" ").build()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request and consent ID are required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_update_voice_consent_without_consent_id() {
        OpenAiUpdateVoiceConsentRequest request =
                OpenAiUpdateVoiceConsentRequest.builder().name("Jane Doe").build();

        assertThatThrownBy(() -> model.updateVoiceConsent(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consent ID is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_update_voice_consent_without_name() {
        OpenAiUpdateVoiceConsentRequest request = OpenAiUpdateVoiceConsentRequest.builder()
                .consentId("cons_123abc")
                .build();

        assertThatThrownBy(() -> model.updateVoiceConsent(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name is required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_delete_voice_consent_without_consent_id() {
        assertThatThrownBy(() -> model.deleteVoiceConsent(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request and consent ID are required");
        assertThatThrownBy(() -> model.deleteVoiceConsent(
                        OpenAiVoiceConsentId.builder().consentId(" ").build()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request and consent ID are required");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_list_voice_consents() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
                {
                  "object": "list",
                  "data": [
                    {
                      "id": "cons_1",
                      "created_at": 1760000000,
                      "language": "en-US",
                      "name": "John Doe",
                      "object": "audio.voice_consent"
                    }
                  ],
                  "first_id": "cons_1",
                  "last_id": "cons_1",
                  "has_more": true
                }
                """));

        OpenAiVoiceConsentListResponse list = model(httpClient)
                .listVoiceConsents(OpenAiListVoiceConsentsRequest.builder()
                        .after("cons_0")
                        .limit(1)
                        .build());

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(GET);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents?after=cons_0&limit=1");

        assertThat(list.data())
                .containsExactly(OpenAiVoiceConsentResponse.builder()
                        .id("cons_1")
                        .createdAt(1760000000L)
                        .language("en-US")
                        .name("John Doe")
                        .object("audio.voice_consent")
                        .build());
        assertThat(list.firstId()).isEqualTo("cons_1");
        assertThat(list.lastId()).isEqualTo("cons_1");
        assertThat(list.hasMore()).isTrue();
    }

    @Test
    void should_list_voice_consents_without_parameters() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
                {"object": "list", "data": [], "has_more": false}
                """));

        OpenAiVoiceConsentListResponse list = model(httpClient).listVoiceConsents();

        assertThat(httpClient.request().url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents");
        assertThat(list.data()).isEmpty();
        assertThat(list.hasMore()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void should_accept_list_voice_consents_limit_at_bounds(int limit) {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
                {"object": "list", "data": [], "has_more": false}
                """));

        model(httpClient)
                .listVoiceConsents(
                        OpenAiListVoiceConsentsRequest.builder().limit(limit).build());

        assertThat(httpClient.request().url())
                .isEqualTo("https://api.openai.com/v1/audio/voice_consents?limit=" + limit);
    }

    @Test
    void should_retrieve_voice_consent() {
        OpenAiVoiceConsentResponse consent = model.retrieveVoiceConsent(
                OpenAiVoiceConsentId.builder().consentId("cons_123abc").build());

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(GET);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents/cons_123abc");

        assertThat(consent)
                .isEqualTo(OpenAiVoiceConsentResponse.builder()
                        .id("cons_123abc")
                        .object("audio.voice_consent")
                        .name("test_consent")
                        .language("en-US")
                        .createdAt(1760000000L)
                        .build());
    }

    @Test
    void should_update_voice_consent() {
        OpenAiVoiceConsentResponse consent = model.updateVoiceConsent(OpenAiUpdateVoiceConsentRequest.builder()
                .consentId("cons_123abc")
                .name("test_consent")
                .build());

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(POST);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents/cons_123abc");
        assertThat(httpRequest.body()).isEqualToIgnoringWhitespace("{\"name\":\"test_consent\"}");

        assertThat(consent.id()).isEqualTo("cons_123abc");
        assertThat(consent.name()).isEqualTo("test_consent");
    }

    @Test
    void should_delete_voice_consent() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
                {
                  "id": "cons_123abc",
                  "deleted": true,
                  "object": "audio.voice_consent"
                }
                """));

        OpenAiDeleteVoiceConsentResponse response = model(httpClient)
                .deleteVoiceConsent(
                        OpenAiVoiceConsentId.builder().consentId("cons_123abc").build());

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(DELETE);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents/cons_123abc");

        assertThat(response)
                .isEqualTo(OpenAiDeleteVoiceConsentResponse.builder()
                        .id("cons_123abc")
                        .deleted(true)
                        .object("audio.voice_consent")
                        .build());
    }

    @Test
    void should_return_open_ai_provider() {
        assertThat(model.provider()).isEqualTo(OPEN_AI);
    }

    private static OpenAiVoiceConsentModel model(MockHttpClient httpClient) {
        return OpenAiVoiceConsentModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test")
                .build();
    }

    private static AudioFile wavFile(byte[] content) {
        return AudioFile.from(
                Audio.builder().binaryData(content).mimeType("audio/wav").build());
    }

    private static SuccessfulHttpResponse jsonResponse(String body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(body.getBytes(UTF_8))
                .build();
    }
}
