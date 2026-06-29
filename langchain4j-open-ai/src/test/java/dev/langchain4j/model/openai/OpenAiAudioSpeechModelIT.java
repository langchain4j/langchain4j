package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.audio.AudioSpeechResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAudioSpeechModelIT {

    Logger log = LoggerFactory.getLogger(OpenAiAudioSpeechModelIT.class);

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(OpenAiAudioSpeechModelName.class)
    void should_support_all_model_names(OpenAiAudioSpeechModelName modelName) throws IOException {
        OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();

        AudioSpeechResponse response = model.generate("Hello world!");

        assertThat(response.audio().binaryData()).isNotNull().isNotEmpty();

        Path audioFile = tempDir.resolve("output.mp3");
        Files.write(audioFile, response.audio().binaryData());
        log.info("Generated audio is here: {}", audioFile);
    }
}
