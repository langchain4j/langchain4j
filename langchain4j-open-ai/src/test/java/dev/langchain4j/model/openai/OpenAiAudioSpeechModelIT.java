package dev.langchain4j.model.openai;

import dev.langchain4j.model.audio.AudioSpeechResponse;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAudioSpeechModelIT {

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
    }
}
