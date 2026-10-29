package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.AudioTranscriptionConfigMode;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Transcription;
import com.google.genai.types.WordInfo;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiAudioTranscriptionIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-3.5-transcribe";

    // a lowercase letter, sentence punctuation and an uppercase letter with no space in between,
    // e.g. "evening.Good", means that two transcription segments were joined without a separator
    private static final String GLUED_SEGMENTS = "[a-z][.!?][A-Z]";

    private byte[] loadSampleAudio() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/sample-voice.mp3")) {
            if (in == null) {
                throw new IllegalStateException("sample-voice.mp3 not found in test resources");
            }
            return in.readAllBytes();
        }
    }

    @Test
    void should_transcribe_audio_with_blocking_chat_model() throws Exception {
        byte[] audioBytes = loadSampleAudio();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .audioTranscriptionConfig(AudioTranscriptionConfig.builder().build())
                .build();

        AudioContent audioContent = AudioContent.from(Base64.getEncoder().encodeToString(audioBytes), "audio/mp3");
        UserMessage message = UserMessage.from(audioContent);

        ChatResponse response = model.chat(message);

        assertThat(response.aiMessage().text())
                .containsIgnoringCase("good evening")
                .containsIgnoringCase("good morning")
                .containsIgnoringCase("LangChain")
                .doesNotContainPattern(GLUED_SEGMENTS);
    }

    @Test
    void should_transcribe_audio_with_smart_mode() throws Exception {
        byte[] audioBytes = loadSampleAudio();

        AudioTranscriptionConfig smartConfig = AudioTranscriptionConfig.builder()
                .mode(AudioTranscriptionConfigMode.Known.SMART)
                .customVocabulary("LangChain4j")
                .build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .audioTranscriptionConfig(smartConfig)
                .build();

        AudioContent audioContent = AudioContent.from(Base64.getEncoder().encodeToString(audioBytes), "audio/mp3");
        UserMessage message = UserMessage.from(audioContent);

        ChatResponse response = model.chat(message);

        assertThat(response.aiMessage().text()).containsIgnoringCase("LangChain4j");
    }

    @Test
    void should_return_word_timestamps_and_speaker_labels_in_raw_response() throws Exception {
        byte[] audioBytes = loadSampleAudio();

        AudioTranscriptionConfig config = AudioTranscriptionConfig.builder()
                .wordTimestamp(true)
                .diarization(true)
                .build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .audioTranscriptionConfig(config)
                .build();

        AudioContent audioContent = AudioContent.from(Base64.getEncoder().encodeToString(audioBytes), "audio/mp3");
        UserMessage message = UserMessage.from(audioContent);

        ChatResponse response = model.chat(message);

        assertThat(response.aiMessage().text())
                .containsIgnoringCase("good evening")
                .containsIgnoringCase("good morning")
                .doesNotContainPattern(GLUED_SEGMENTS);

        GenerateContentResponse rawResponse = ((GoogleGenAiChatResponseMetadata) response.metadata()).rawResponse();
        List<Transcription> transcriptions = rawResponse.parts().stream()
                .map(Part::audioTranscription)
                .flatMap(Optional::stream)
                .toList();
        assertThat(transcriptions).isNotEmpty();
        assertThat(transcriptions)
                .anySatisfy(transcription ->
                        assertThat(transcription.speakerLabel()).isPresent());

        List<WordInfo> words = transcriptions.stream()
                .flatMap(transcription -> transcription.words().orElse(List.of()).stream())
                .toList();
        assertThat(words).isNotEmpty().allSatisfy(word -> {
            assertThat(word.word()).isPresent();
            assertThat(word.startOffset()).isPresent();
            assertThat(word.endOffset()).isPresent();
        });
    }

    @Test
    void should_transcribe_audio_with_streaming_chat_model() throws Exception {
        byte[] audioBytes = loadSampleAudio();

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .audioTranscriptionConfig(AudioTranscriptionConfig.builder().build())
                .build();

        AudioContent audioContent = AudioContent.from(Base64.getEncoder().encodeToString(audioBytes), "audio/mp3");
        UserMessage message = UserMessage.from(audioContent);

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(message), handler);

        assertThat(handler.get().aiMessage().text())
                .containsIgnoringCase("LangChain")
                .doesNotContainPattern(GLUED_SEGMENTS);
    }
}
