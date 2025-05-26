package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.Response;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the Azure OpenAI audio model.
 */
public class AzureOpenAiAudioTranscriptionModelIT {

    @Test
    void should_transcribe_audio() throws IOException {
        AzureOpenAiAudioTranscriptionModel model = AzureOpenAiAudioTranscriptionModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("whisper")
                .logRequestsAndResponses(true)
                .build();

        File audioFile = new File("src/test/resources/hello.mp3");
        String audioData = Base64.getEncoder().encodeToString(FileUtils.readFileToByteArray(audioFile));

        Audio audio =
                Audio.builder().base64Data(audioData).mimeType("audio/mpeg").build();

        Response<String> response = model.transcribe(audio);
        String message = response.content().toString();
        assertThat(message).isNotNull();
        assertThat(message).containsAnyOf("Hello");
    }
}
