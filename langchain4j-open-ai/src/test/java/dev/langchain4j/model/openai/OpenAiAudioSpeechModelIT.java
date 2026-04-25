package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAudioSpeechModelIT {

    Logger log = LoggerFactory.getLogger(OpenAiImageModelIT.class);

    @ParameterizedTest
    @EnumSource(OpenAiAudioSpeechModelName.class)
    void should_support_all_model_names(OpenAiAudioSpeechModelName modelName) {
        OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();
        byte[] response = model.generate("Hello world!");
        try {
            Files.write(Path.of("output.mp3"), response);
            log.info("Your remote image is here: {}", Path.of("output.mp3"));
            assertThat(response).isNotNull();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
