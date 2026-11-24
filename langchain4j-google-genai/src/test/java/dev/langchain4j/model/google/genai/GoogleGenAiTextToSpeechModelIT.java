package dev.langchain4j.model.google.genai;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiTextToSpeechModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @ParameterizedTest
    @ValueSource(strings = {"gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts", "gemini-3.1-flash-tts-preview"})
    void should_synthesize_wav_speech_of_the_text(String modelName) {
        GoogleGenAiTextToSpeechModel model = GoogleGenAiTextToSpeechModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(modelName)
                .voice("Kore")
                .build();

        Audio audio =
                model.synthesize("The quick brown fox jumps over the lazy dog.").audio();

        assertThat(audio.mimeType()).isEqualTo("audio/wav");
        assertThat(new String(Arrays.copyOfRange(audio.binaryData(), 0, 4), US_ASCII))
                .isEqualTo("RIFF");
        assertThat(transcribe(audio)).containsIgnoringCase("brown fox").containsIgnoringCase("lazy dog");
    }

    @Test
    void should_synthesize_speech_in_requested_language() {
        GoogleGenAiTextToSpeechModel model = GoogleGenAiTextToSpeechModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-3.8-flash-tts")
                .languageCode("de-DE")
                .build();

        Audio audio = model.synthesize("Guten Morgen, wie geht es dir?").audio();

        assertThat(transcribe(audio)).containsIgnoringCase("Guten Morgen");
    }

    @Test
    void should_speak_every_turn_of_a_conversation() {
        GoogleGenAiTextToSpeechModel model = GoogleGenAiTextToSpeechModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-3.8-flash-tts")
                .speaker("Joe", "Kore")
                .speaker("Jane", "Puck")
                .build();

        Audio audio = model.synthesize("Joe: How is the weather in Paris today?\nJane: Sunny and warm.")
                .audio();

        assertThat(transcribe(audio))
                .containsIgnoringCase("weather in Paris")
                .containsIgnoringCase("sunny and warm")
                .doesNotContain("Joe:")
                .doesNotContain("Jane:");
    }

    private static String transcribe(Audio audio) {
        // A general model, not gemini-3.5-transcribe: the CI key has only 100 requests per day for that model,
        // shared by every CI run, and GoogleGenAiAudioTranscriptionIT already covers it.
        GoogleGenAiChatModel transcriber = GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-3.8-flash")
                .temperature(0.0)
                .build();

        UserMessage message = UserMessage.from(
                TextContent.from("Transcribe this audio word for word, in the language that is spoken. "
                        + "Reply with the transcript only."),
                AudioContent.from(Base64.getEncoder().encodeToString(audio.binaryData()), audio.mimeType()));

        return transcriber.chat(message).aiMessage().text();
    }
}
