package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.AudioTranscription;
import com.azure.ai.openai.models.AudioTranscriptionFormat;
import com.azure.ai.openai.models.AudioTranscriptionOptions;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AzureOpenAiAudioTranscriptionModelTest {

    @Test
    void should_create_model_with_builder() {
        // when
        AzureOpenAiAudioTranscriptionModel model = AzureOpenAiAudioTranscriptionModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("test-deployment")
                .build();

        // then
        assertThat(model).isNotNull();
    }

    @Test
    void should_create_model_with_client_and_deployment() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        String deploymentName = "test-deployment";

        // when
        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, deploymentName, AudioTranscriptionFormat.JSON);

        // then
        assertThat(model).isNotNull();
    }

    @Test
    void should_throw_exception_when_client_is_null() {
        // when & then
        assertThatThrownBy(
                        () -> new AzureOpenAiAudioTranscriptionModel(null, "deployment", AudioTranscriptionFormat.JSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client is required");
    }

    @Test
    void should_throw_exception_when_deployment_name_is_null() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);

        // when & then
        assertThatThrownBy(() -> new AzureOpenAiAudioTranscriptionModel(client, null, AudioTranscriptionFormat.JSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deploymentName is required");
    }

    @Test
    void should_throw_exception_when_deployment_name_is_blank() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);

        // when & then
        assertThatThrownBy(() -> new AzureOpenAiAudioTranscriptionModel(client, "", AudioTranscriptionFormat.JSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deploymentName is required");
    }

    @Test
    void should_transcribe_audio_with_binary_data() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AudioTranscription mockTranscription = mock(AudioTranscription.class);
        when(mockTranscription.getText()).thenReturn("Transcribed text");
        when(client.getAudioTranscription(any(), any(), any(AudioTranscriptionOptions.class)))
                .thenReturn(mockTranscription);

        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        byte[] audioData = "test audio data".getBytes();
        Audio audio =
                Audio.builder().binaryData(audioData).mimeType("audio/wav").build();

        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        // when
        AudioTranscriptionResponse response = model.transcribe(request);

        // then
        assertThat(response.text()).isEqualTo("Transcribed text");
        verify(client)
                .getAudioTranscription(eq("test-deployment"), eq("audio.mp3"), any(AudioTranscriptionOptions.class));
    }

    @Test
    void should_transcribe_audio_with_base64_data() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AudioTranscription mockTranscription = mock(AudioTranscription.class);
        when(mockTranscription.getText()).thenReturn("Transcribed base64 text");
        when(client.getAudioTranscription(any(), any(), any(AudioTranscriptionOptions.class)))
                .thenReturn(mockTranscription);

        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        byte[] originalData = "test audio data".getBytes();
        String base64Data = Base64.getEncoder().encodeToString(originalData);
        Audio audio =
                Audio.builder().base64Data(base64Data).mimeType("audio/wav").build();

        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        // when
        AudioTranscriptionResponse response = model.transcribe(request);

        // then
        assertThat(response.text()).isEqualTo("Transcribed base64 text");

        ArgumentCaptor<AudioTranscriptionOptions> optionsCaptor =
                ArgumentCaptor.forClass(AudioTranscriptionOptions.class);
        verify(client).getAudioTranscription(eq("test-deployment"), eq("audio.mp3"), optionsCaptor.capture());
    }

    @Test
    void should_throw_exception_for_url_based_audio() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        Audio audio = Audio.builder()
                .url("https://example.com/audio.wav")
                .mimeType("audio/wav")
                .build();

        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        // when & then
        assertThatThrownBy(() -> model.transcribe(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL-based audio is not supported");
    }

    @Test
    void should_throw_exception_when_no_audio_data() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        Audio audio = Audio.builder().mimeType("audio/wav").build();

        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        // when & then
        assertThatThrownBy(() -> model.transcribe(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No audio data found");
    }

    @Test
    void should_throw_exception_when_request_is_null() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        // when & then
        assertThatThrownBy(() -> model.transcribe(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request and audio data are required");
    }

    @Test
    void should_throw_exception_when_audio_is_null() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        // when & then - the builder itself should throw the exception
        assertThatThrownBy(() -> AudioTranscriptionRequest.builder().audio(null).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Audio must be provided");
    }

    @Test
    void should_handle_invalid_base64_data() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        Audio audio = Audio.builder()
                .base64Data("invalid-base64-data!")
                .mimeType("audio/wav")
                .build();

        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        // when & then
        assertThatThrownBy(() -> model.transcribe(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid base64 audio data provided");
    }

    @Test
    void should_use_default_response_format_when_null() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);

        // when
        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", null);

        // then - should not throw exception, default format should be used
        assertThat(model).isNotNull();
    }

    @Test
    void should_transcribe_with_all_request_parameters() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AudioTranscription mockTranscription = mock(AudioTranscription.class);
        when(mockTranscription.getText()).thenReturn("Full parameter transcription");
        when(client.getAudioTranscription(any(), any(), any(AudioTranscriptionOptions.class)))
                .thenReturn(mockTranscription);

        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .prompt("Test prompt")
                .language("en")
                .temperature(0.5)
                .build();

        // when
        AudioTranscriptionResponse response = model.transcribe(request);

        // then
        assertThat(response.text()).isEqualTo("Full parameter transcription");

        ArgumentCaptor<AudioTranscriptionOptions> optionsCaptor =
                ArgumentCaptor.forClass(AudioTranscriptionOptions.class);
        verify(client).getAudioTranscription(eq("test-deployment"), eq("audio.mp3"), optionsCaptor.capture());
    }

    @Test
    void should_use_default_filename() {
        // given
        OpenAIClient client = mock(OpenAIClient.class);
        AudioTranscription mockTranscription = mock(AudioTranscription.class);
        when(mockTranscription.getText()).thenReturn("Transcribed with default filename");
        when(client.getAudioTranscription(any(), any(), any(AudioTranscriptionOptions.class)))
                .thenReturn(mockTranscription);

        AzureOpenAiAudioTranscriptionModel model =
                new AzureOpenAiAudioTranscriptionModel(client, "test-deployment", AudioTranscriptionFormat.JSON);

        // Create audio with binary data (no URL)
        Audio audio = Audio.builder()
                .binaryData("test audio".getBytes())
                .mimeType("audio/wav")
                .build();

        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        // when
        model.transcribe(request);

        // then - should use default filename "audio.mp3"
        verify(client)
                .getAudioTranscription(eq("test-deployment"), eq("audio.mp3"), any(AudioTranscriptionOptions.class));
    }
}
