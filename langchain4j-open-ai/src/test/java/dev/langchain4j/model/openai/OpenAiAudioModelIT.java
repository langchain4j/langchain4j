package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAudioModelIT {

    @Test
    void should_support_all_model_names() throws Exception {
        // given
        var model = OpenAiAudioModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(OpenAiAudioModelName.WHISPER_1)
                .logRequests(false)
                .logResponses(false)
                .build();

        Audio audio = Audio.builder()
                .binaryData(Files.readAllBytes(Path.of(
                        getClass().getClassLoader().getResource("sample.wav").toURI())))
                .mimeType("audio/wav")
                .build();

        // when
        var audioTranscriptionResponse = model.transcribe(audio);

        // then
        assertThat(audioTranscriptionResponse.text()).containsIgnoringCase("Hello");
    }
}
