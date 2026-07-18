package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiLiveModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void streams_audio_and_a_transcript_for_a_text_turn() throws Exception {
        GoogleGenAiLiveModel model = GoogleGenAiLiveModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-native-audio-latest")
                .responseModalities("AUDIO")
                .voiceName("Puck")
                .outputAudioTranscription(true)
                .build();

        AtomicInteger audioChunks = new AtomicInteger();
        StringBuilder transcript = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch turnComplete = new CountDownLatch(1);

        try (GoogleGenAiLiveSession session = model.connect(new GoogleGenAiLiveResponseHandler() {
            @Override
            public void onAudio(byte[] audio) {
                if (audio.length > 0) {
                    audioChunks.incrementAndGet();
                }
            }

            @Override
            public void onOutputTranscription(String text) {
                transcript.append(text);
            }

            @Override
            public void onTurnComplete() {
                turnComplete.countDown();
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                turnComplete.countDown();
            }
        })) {
            assertThat(session.isOpen()).isTrue();

            session.sendText("Reply with a short spoken greeting.");

            assertThat(turnComplete.await(60, TimeUnit.SECONDS))
                    .as("expected a completed turn within 60s")
                    .isTrue();
        }

        assertThat(error.get()).isNull();
        assertThat(audioChunks.get()).as("expected at least one audio chunk").isPositive();
        assertThat(transcript.toString()).isNotBlank();
    }
}
