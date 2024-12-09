package dev.langchain4j.model.azure;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.output.Response;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

/**
 * Integration test for the Azure OpenAI audio model.
 */

public class AzureOpenAiAudioModelIT {

    @Test
    void should_transcribe_audio() throws IOException {
        AzureOpenAiAudioModel model = AzureOpenAiAudioModel.builder()
                 .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                 .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                 .deploymentName("whisper")
                 .logRequestsAndResponses(true)
                 .build();

        File audioFile = new File("src/test/resources/hello.mp3");
        byte[] audioData = FileUtils.readFileToByteArray(audioFile);

        Audio audio = Audio.builder()
                 .audioData(audioData)
                 .mimeType("audio/mpeg")
                 .url(audioFile.toURI())
                 .build();
        
        Response<String> response = model.transcribe(audio);
        String message = response.content().toString();
        assertThat(message).isNotNull();
        assertThat(message).containsAnyOf("Hello");
    }   
}
