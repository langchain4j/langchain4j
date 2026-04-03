package dev.langchain4j.model.openai;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAudioTranscriptionModelIT {

    @ParameterizedTest
    @EnumSource(OpenAiAudioTranscriptionModelName.class)
    void should_support_all_model_names(OpenAiAudioTranscriptionModelName modelName) throws Exception {

        // given
        var model = OpenAiAudioTranscriptionModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();

        Path audioPath = Path.of(getClass().getClassLoader().getResource("sample.wav").toURI());

        Audio audio = Audio.builder()
                .binaryData(Files.readAllBytes(audioPath))
                .mimeType("audio/wav")
                .build();

        // when
        String transcription = model.transcribeToText(audio);

        // then
        assertThat(transcription).containsIgnoringCase("hello");
    }
}
