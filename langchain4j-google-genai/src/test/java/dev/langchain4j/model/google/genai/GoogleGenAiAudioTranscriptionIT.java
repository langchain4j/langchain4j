package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.AudioTranscriptionConfigMode;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiAudioTranscriptionIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-3.5-transcribe";

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
                .containsIgnoringCase("LangChain");
    }

    @Test
    void should_transcribe_audio_with_smart_mode() throws Exception {
        byte[] audioBytes = loadSampleAudio();

        AudioTranscriptionConfig smartConfig = AudioTranscriptionConfig.builder()
                .mode(AudioTranscriptionConfigMode.Known.SMART)
                .build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .audioTranscriptionConfig(smartConfig)
                .build();

        AudioContent audioContent = AudioContent.from(Base64.getEncoder().encodeToString(audioBytes), "audio/mp3");
        UserMessage message = UserMessage.from(audioContent);

        ChatResponse response = model.chat(message);

        assertThat(response.aiMessage().text()).contains("LangChain4j");
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

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        StringBuilder streamedChunks = new StringBuilder();

        model.chat(List.of(message), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                streamedChunks.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get();

        assertThat(response.aiMessage().text()).containsIgnoringCase("LangChain");
        assertThat(streamedChunks.toString()).isEqualTo(response.aiMessage().text());
    }
}
