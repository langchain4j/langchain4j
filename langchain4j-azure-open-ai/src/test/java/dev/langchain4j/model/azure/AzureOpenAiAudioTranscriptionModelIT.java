package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_AUDIO_ENDPOINT", matches = ".+")
public class AzureOpenAiAudioTranscriptionModelIT {

    AudioTranscriptionModel model = AzureOpenAiAudioTranscriptionModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_AUDIO_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_AUDIO_KEY"))
            .deploymentName(System.getenv("AZURE_OPENAI_AUDIO_DEPLOYMENT_NAME"))
            .logRequestsAndResponses(true)
            .build();

    @Test
    void should_transcribe_audio() throws IOException {

        File audioFile = new File("src/test/resources/hello.mp3");
        byte[] binaryData = FileUtils.readFileToByteArray(audioFile);
        Audio audio = Audio.builder().binaryData(binaryData).build();

        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(audio)
                .language("en")
                .temperature(0.0)
                .build();

        AudioTranscriptionResponse response = model.transcribe(request);

        assertThat(response.text()).containsAnyOf("Hello", "Hallo", "LangChain", "Langchain");
    }

    @Test
    void should_transcribe_audio_with_simple_api() throws IOException {

        File audioFile = new File("src/test/resources/hello.mp3");
        byte[] binaryData = FileUtils.readFileToByteArray(audioFile);
        Audio audio = Audio.builder().binaryData(binaryData).build();

        String text = model.transcribeToText(audio);

        assertThat(text).containsAnyOf("Hello", "Hallo", "LangChain", "Langchain");
    }
}
