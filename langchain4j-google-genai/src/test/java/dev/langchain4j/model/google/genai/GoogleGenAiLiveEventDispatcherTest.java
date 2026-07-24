package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Content;
import com.google.genai.types.LiveServerContent;
import com.google.genai.types.LiveServerGoAway;
import com.google.genai.types.LiveServerMessage;
import com.google.genai.types.LiveServerSessionResumptionUpdate;
import com.google.genai.types.Part;
import com.google.genai.types.Transcription;
import com.google.genai.types.UsageMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleGenAiLiveEventDispatcherTest {

    private final RecordingHandler handler = new RecordingHandler();
    private final GoogleGenAiLiveEventDispatcher dispatcher = new GoogleGenAiLiveEventDispatcher(handler);

    @Test
    void streams_text_parts_then_completes_turn_accumulating_across_messages() {
        dispatcher.dispatch(textMessage("Hel"));
        dispatcher.dispatch(textMessage("lo"));
        dispatcher.dispatch(LiveServerMessage.builder()
                .serverContent(LiveServerContent.builder().turnComplete(true).build())
                .build());

        assertThat(handler.partialTexts).containsExactly("Hel", "lo");
        assertThat(handler.completeTexts).containsExactly("Hello");
        assertThat(handler.turnComplete).isEqualTo(1);
    }

    @Test
    void routes_output_audio() {
        byte[] audio = {1, 2, 3, 4};
        dispatcher.dispatch(LiveServerMessage.builder()
                .serverContent(LiveServerContent.builder()
                        .modelTurn(Content.fromParts(Part.fromBytes(audio, "audio/pcm;rate=24000")))
                        .build())
                .build());

        assertThat(handler.audioChunks).hasSize(1);
        assertThat(handler.audioChunks.get(0)).isEqualTo(audio);
        assertThat(handler.completeTexts).isEmpty();
    }

    @Test
    void processes_audio_and_transcript_in_a_single_message() {
        byte[] audio = {9, 8, 7};
        dispatcher.dispatch(LiveServerMessage.builder()
                .serverContent(LiveServerContent.builder()
                        .modelTurn(Content.fromParts(Part.fromBytes(audio, "audio/pcm;rate=24000")))
                        .outputTranscription(
                                Transcription.builder().text("hello there").build())
                        .build())
                .build());

        assertThat(handler.audioChunks).hasSize(1);
        assertThat(handler.outputTranscripts).containsExactly("hello there");
    }

    @Test
    void routes_input_and_output_transcription() {
        dispatcher.dispatch(LiveServerMessage.builder()
                .serverContent(LiveServerContent.builder()
                        .inputTranscription(
                                Transcription.builder().text("user said").build())
                        .outputTranscription(
                                Transcription.builder().text("model said").build())
                        .build())
                .build());

        assertThat(handler.inputTranscripts).containsExactly("user said");
        assertThat(handler.outputTranscripts).containsExactly("model said");
    }

    @Test
    void interruption_fires_event_and_discards_pending_text() {
        dispatcher.dispatch(textMessage("partial answer"));
        dispatcher.dispatch(LiveServerMessage.builder()
                .serverContent(LiveServerContent.builder().interrupted(true).build())
                .build());
        dispatcher.dispatch(LiveServerMessage.builder()
                .serverContent(LiveServerContent.builder().turnComplete(true).build())
                .build());

        assertThat(handler.interrupted).isEqualTo(1);
        assertThat(handler.completeTexts).isEmpty();
    }

    @Test
    void generation_complete_fires_without_completing_the_turn() {
        dispatcher.dispatch(LiveServerMessage.builder()
                .serverContent(
                        LiveServerContent.builder().generationComplete(true).build())
                .build());

        assertThat(handler.generationComplete).isEqualTo(1);
        assertThat(handler.turnComplete).isZero();
    }

    @Test
    void generation_complete_precedes_turn_complete_within_one_message() {
        dispatcher.dispatch(LiveServerMessage.builder()
                .serverContent(LiveServerContent.builder()
                        .generationComplete(true)
                        .turnComplete(true)
                        .build())
                .build());

        assertThat(handler.lifecycle).containsExactly("generation", "turn");
    }

    @Test
    void routes_usage_metadata_to_token_usage() {
        dispatcher.dispatch(LiveServerMessage.builder()
                .usageMetadata(UsageMetadata.builder()
                        .promptTokenCount(3)
                        .responseTokenCount(5)
                        .totalTokenCount(8)
                        .build())
                .build());

        assertThat(handler.usages).containsExactly(new TokenUsage(3, 5, 8));
    }

    @Test
    void routes_go_away_and_session_resumption_update() {
        dispatcher.dispatch(LiveServerMessage.builder()
                .goAway(LiveServerGoAway.builder()
                        .timeLeft(Duration.ofSeconds(10))
                        .build())
                .sessionResumptionUpdate(LiveServerSessionResumptionUpdate.builder()
                        .newHandle("handle-123")
                        .build())
                .build());

        assertThat(handler.goAways).containsExactly(Duration.ofSeconds(10));
        assertThat(handler.resumptionHandles).containsExactly("handle-123");
    }

    private static LiveServerMessage textMessage(String text) {
        return LiveServerMessage.builder()
                .serverContent(LiveServerContent.builder()
                        .modelTurn(Content.fromParts(Part.fromText(text)))
                        .build())
                .build();
    }

    private static final class RecordingHandler implements GoogleGenAiLiveResponseHandler {
        int turnComplete;
        int generationComplete;
        int interrupted;
        final List<String> lifecycle = new ArrayList<>();
        final List<String> partialTexts = new ArrayList<>();
        final List<String> completeTexts = new ArrayList<>();
        final List<byte[]> audioChunks = new ArrayList<>();
        final List<String> inputTranscripts = new ArrayList<>();
        final List<String> outputTranscripts = new ArrayList<>();
        final List<TokenUsage> usages = new ArrayList<>();
        final List<Duration> goAways = new ArrayList<>();
        final List<String> resumptionHandles = new ArrayList<>();

        @Override
        public void onPartialText(String text) {
            partialTexts.add(text);
        }

        @Override
        public void onCompleteText(String text) {
            completeTexts.add(text);
        }

        @Override
        public void onAudio(byte[] audio) {
            audioChunks.add(audio);
        }

        @Override
        public void onInputTranscription(String text) {
            inputTranscripts.add(text);
        }

        @Override
        public void onOutputTranscription(String text) {
            outputTranscripts.add(text);
        }

        @Override
        public void onGenerationComplete() {
            generationComplete++;
            lifecycle.add("generation");
        }

        @Override
        public void onTurnComplete() {
            turnComplete++;
            lifecycle.add("turn");
        }

        @Override
        public void onInterrupted() {
            interrupted++;
        }

        @Override
        public void onUsageMetadata(TokenUsage tokenUsage) {
            usages.add(tokenUsage);
        }

        @Override
        public void onGoAway(Duration timeLeft) {
            goAways.add(timeLeft);
        }

        @Override
        public void onSessionResumptionUpdate(String handle) {
            resumptionHandles.add(handle);
        }
    }
}
