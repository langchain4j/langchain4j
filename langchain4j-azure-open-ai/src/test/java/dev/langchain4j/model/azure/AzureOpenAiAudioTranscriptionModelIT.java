package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.azure.ai.openai.models.AudioTranscriptionFormat;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for the Azure OpenAI audio transcription model.
 */
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_AUDIO_ENDPOINT", matches = ".+")
public class AzureOpenAiAudioTranscriptionModelIT {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiAudioTranscriptionModelIT.class);

    @Test
    void should_transcribe_audio_with_new_api() throws IOException {
        String endpoint = System.getenv("AZURE_OPENAI_AUDIO_ENDPOINT");
        String apiKey = System.getenv("AZURE_OPENAI_AUDIO_KEY");
        String deploymentName = System.getenv("AZURE_OPENAI_AUDIO_DEPLOYMENT_NAME");

        // Skip test if required environment variables are not set
        assumeTrue(apiKey != null && !apiKey.isBlank(), "AZURE_OPENAI_AUDIO_KEY environment variable not set");
        assumeTrue(
                deploymentName != null && !deploymentName.isBlank(),
                "AZURE_OPENAI_AUDIO_DEPLOYMENT_NAME environment variable not set");

        AzureOpenAiAudioTranscriptionModel model = AzureOpenAiAudioTranscriptionModel.builder()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .deploymentName(deploymentName)
                .responseFormat(AudioTranscriptionFormat.JSON)
                .logRequestsAndResponses(true)
                .build();

        File audioFile = new File("src/test/resources/hello.mp3");
        // Skip test if audio file is not available
        assumeTrue(audioFile.exists(), "Test audio file not found: src/test/resources/hello.mp3");

        byte[] audioData = FileUtils.readFileToByteArray(audioFile);
        Audio audio =
                Audio.builder().binaryData(audioData).mimeType("audio/mpeg").build();

        // Test with new request/response objects
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .language("en")
                .temperature(0.0)
                .build();

        AudioTranscriptionResponse response = model.transcribe(request);
        String text = response.text();
        assertThat(text).isNotNull();
        assertThat(text).containsAnyOf("Hello", "Hallo", "LangChain", "Langchain");

        logger.info("Successfully transcribed audio with new API: {}", text);
    }

    @Test
    void should_transcribe_audio_with_simple_api() throws IOException {
        String endpoint = System.getenv("AZURE_OPENAI_AUDIO_ENDPOINT");
        String apiKey = System.getenv("AZURE_OPENAI_AUDIO_KEY");
        String deploymentName = System.getenv("AZURE_OPENAI_AUDIO_DEPLOYMENT_NAME");

        // Skip test if required environment variables are not set
        assumeTrue(apiKey != null && !apiKey.isBlank(), "AZURE_OPENAI_AUDIO_KEY environment variable not set");
        assumeTrue(
                deploymentName != null && !deploymentName.isBlank(),
                "AZURE_OPENAI_AUDIO_DEPLOYMENT_NAME environment variable not set");

        AzureOpenAiAudioTranscriptionModel model = AzureOpenAiAudioTranscriptionModel.builder()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .deploymentName(deploymentName)
                .responseFormat(AudioTranscriptionFormat.JSON)
                .logRequestsAndResponses(true)
                .build();

        File audioFile = new File("src/test/resources/hello.mp3");
        // Skip test if audio file is not available
        assumeTrue(audioFile.exists(), "Test audio file not found: src/test/resources/hello.mp3");

        byte[] audioData = FileUtils.readFileToByteArray(audioFile);
        Audio audio =
                Audio.builder().binaryData(audioData).mimeType("audio/mpeg").build();

        // Test with simple request/response objects (no optional parameters)
        AudioTranscriptionRequest request =
                AudioTranscriptionRequest.builder().audio(audio).build();

        AudioTranscriptionResponse response = model.transcribe(request);
        String text = response.text();
        assertThat(text).isNotNull();
        assertThat(text).containsAnyOf("Hello", "Hallo", "LangChain", "Langchain");

        logger.info("Successfully transcribed audio with simple API: {}", text);
    }
}
