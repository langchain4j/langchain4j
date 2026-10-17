package dev.langchain4j.model.openai.internal;

import static dev.langchain4j.http.client.HttpMethod.DELETE;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.http.client.FormDataFile;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.openai.internal.audio.transcription.AudioFile;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceConsentRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceResponse;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiDeleteVoiceConsentResponse;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiListVoiceConsentsRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiUpdateVoiceConsentRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentId;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentListResponse;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentResponse;
import org.junit.jupiter.api.Test;

class DefaultOpenAiClientVoiceTest {

    private static final byte[] WAV_BYTES = {1, 2, 3, 4};

    @Test
    void should_upload_voice_consent() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
                {
                  "id": "cons_123abc",
                  "object": "audio.voice_consent",
                  "name": "test_consent",
                  "language": "en-US",
                  "created_at": 1760000000
                }
                """));

        OpenAiCreateVoiceConsentRequest request = OpenAiCreateVoiceConsentRequest.builder()
                .name("test_consent")
                .language("en-US")
                .recording(wavFile())
                .build();

        OpenAiVoiceConsentResponse consent =
                client(httpClient).createVoiceConsent(request).execute();

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(POST);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents");
        assertThat(httpRequest.formDataFields())
                .containsEntry("name", "test_consent")
                .containsEntry("language", "en-US");
        FormDataFile recording = httpRequest.formDataFiles().get("recording");
        assertThat(recording.contentType()).isEqualTo("audio/wav");
        assertThat(recording.content()).containsExactly(WAV_BYTES);

        assertThat(consent.id()).isEqualTo("cons_123abc");
        assertThat(consent.object()).isEqualTo("audio.voice_consent");
        assertThat(consent.name()).isEqualTo("test_consent");
        assertThat(consent.language()).isEqualTo("en-US");
        assertThat(consent.createdAt()).isEqualTo(1760000000L);
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
                    },
                    {
                      "id": "cons_2",
                      "created_at": 1760000001,
                      "language": "fr-FR",
                      "name": "Jane Doe",
                      "object": "audio.voice_consent"
                    }
                  ],
                  "first_id": "cons_1",
                  "last_id": "cons_2",
                  "has_more": true
                }
                """));

        OpenAiListVoiceConsentsRequest request = OpenAiListVoiceConsentsRequest.builder()
                .after("cons_0")
                .limit(2)
                .build();

        OpenAiVoiceConsentListResponse list =
                client(httpClient).listVoiceConsents(request).execute();

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(GET);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents?after=cons_0&limit=2");

        assertThat(list.object()).isEqualTo("list");
        assertThat(list.firstId()).isEqualTo("cons_1");
        assertThat(list.lastId()).isEqualTo("cons_2");
        assertThat(list.hasMore()).isTrue();
        assertThat(list.data())
                .containsExactly(
                        OpenAiVoiceConsentResponse.builder()
                                .id("cons_1")
                                .createdAt(1760000000L)
                                .language("en-US")
                                .name("John Doe")
                                .object("audio.voice_consent")
                                .build(),
                        OpenAiVoiceConsentResponse.builder()
                                .id("cons_2")
                                .createdAt(1760000001L)
                                .language("fr-FR")
                                .name("Jane Doe")
                                .object("audio.voice_consent")
                                .build());
    }

    @Test
    void should_list_voice_consents_without_query_parameters() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse(
                "{\"object\": \"list\", \"data\": [], \"first_id\": null, \"last_id\": null, \"has_more\": false}"));

        OpenAiVoiceConsentListResponse list = client(httpClient)
                .listVoiceConsents(OpenAiListVoiceConsentsRequest.builder().build())
                .execute();

        assertThat(httpClient.request().url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents");
        assertThat(list.data()).isEmpty();
        assertThat(list.firstId()).isNull();
        assertThat(list.hasMore()).isFalse();
    }

    @Test
    void should_retrieve_voice_consent() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
                {
                  "id": "cons_123abc",
                  "created_at": 1760000000,
                  "language": "en-US",
                  "name": "John Doe",
                  "object": "audio.voice_consent"
                }
                """));

        OpenAiVoiceConsentId request =
                OpenAiVoiceConsentId.builder().consentId("cons_123abc").build();

        OpenAiVoiceConsentResponse consent =
                client(httpClient).retrieveVoiceConsent(request).execute();

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(GET);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents/cons_123abc");

        assertThat(consent)
                .isEqualTo(OpenAiVoiceConsentResponse.builder()
                        .id("cons_123abc")
                        .createdAt(1760000000L)
                        .language("en-US")
                        .name("John Doe")
                        .object("audio.voice_consent")
                        .build());
    }

    @Test
    void should_update_voice_consent() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
                {
                  "id": "cons_123abc",
                  "created_at": 1760000000,
                  "language": "en-US",
                  "name": "Jane Doe",
                  "object": "audio.voice_consent"
                }
                """));

        OpenAiUpdateVoiceConsentRequest request = OpenAiUpdateVoiceConsentRequest.builder()
                .consentId("cons_123abc")
                .name("Jane Doe")
                .build();

        OpenAiVoiceConsentResponse consent =
                client(httpClient).updateVoiceConsent(request).execute();

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(POST);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voice_consents/cons_123abc");
        assertThat(httpRequest.headers().get("Content-Type")).containsExactly("application/json");
        assertThat(httpRequest.body()).isEqualToIgnoringWhitespace("{\"name\":\"Jane Doe\"}");

        assertThat(consent)
                .isEqualTo(OpenAiVoiceConsentResponse.builder()
                        .id("cons_123abc")
                        .createdAt(1760000000L)
                        .language("en-US")
                        .name("Jane Doe")
                        .object("audio.voice_consent")
                        .build());
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

        OpenAiVoiceConsentId request =
                OpenAiVoiceConsentId.builder().consentId("cons_123abc").build();

        OpenAiDeleteVoiceConsentResponse response =
                client(httpClient).deleteVoiceConsent(request).execute();

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
    void should_create_voice() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("""
                {
                  "id": "voice_123abc",
                  "created_at": 1760000000,
                  "name": "My new voice",
                  "object": "audio.voice",
                  "type": "prompt",
                  "prompt": "A warm, calm narrator voice"
                }
                """));

        OpenAiCreateVoiceRequest request = OpenAiCreateVoiceRequest.builder()
                .name("My new voice")
                .prompt("A warm, calm narrator voice")
                .model("auto")
                .scriptHint("Hello, welcome to the show.")
                .build();

        OpenAiCreateVoiceResponse voice =
                client(httpClient).createVoice(request).execute();

        HttpRequest httpRequest = httpClient.request();
        assertThat(httpRequest.method()).isEqualTo(POST);
        assertThat(httpRequest.url()).isEqualTo("https://api.openai.com/v1/audio/voices");
        assertThat(httpRequest.headers().get("Content-Type")).containsExactly("application/json");
        assertThat(httpRequest.body()).isEqualToIgnoringWhitespace("""
                        {
                          "name": "My new voice",
                          "prompt": "A warm, calm narrator voice",
                          "type": "prompt",
                          "model": "auto",
                          "script_hint": "Hello, welcome to the show."
                        }
                        """);

        assertThat(voice)
                .isEqualTo(OpenAiCreateVoiceResponse.builder()
                        .id("voice_123abc")
                        .createdAt(1760000000L)
                        .name("My new voice")
                        .object("audio.voice")
                        .type("prompt")
                        .prompt("A warm, calm narrator voice")
                        .build());
    }

    @Test
    void should_omit_optional_fields_when_creating_voice() {
        MockHttpClient httpClient = new MockHttpClient(jsonResponse("{\"id\": \"voice_123abc\"}"));

        OpenAiCreateVoiceRequest request = OpenAiCreateVoiceRequest.builder()
                .name("My new voice")
                .prompt("A warm, calm narrator voice")
                .build();

        client(httpClient).createVoice(request).execute();

        assertThat(httpClient.request().body())
                .isEqualToIgnoringWhitespace(
                        "{\"name\": \"My new voice\", \"prompt\": \"A warm, calm narrator voice\", \"type\": \"prompt\"}");
    }

    private static AudioFile wavFile() {
        return AudioFile.from(
                Audio.builder().binaryData(WAV_BYTES).mimeType("audio/wav").build());
    }

    private static SuccessfulHttpResponse jsonResponse(String body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(body.getBytes(UTF_8))
                .build();
    }

    private static OpenAiClient client(MockHttpClient httpClient) {
        return OpenAiClient.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("https://api.openai.com/v1")
                .apiKey("test")
                .build();
    }
}
