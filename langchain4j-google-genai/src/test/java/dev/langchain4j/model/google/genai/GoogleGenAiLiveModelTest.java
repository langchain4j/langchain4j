package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.google.genai.GoogleGenAiLiveSessionImpl.clientContent;
import static dev.langchain4j.model.google.genai.GoogleGenAiLiveSessionImpl.realtimeAudio;
import static dev.langchain4j.model.google.genai.GoogleGenAiLiveSessionImpl.realtimeAudioStreamEnd;
import static dev.langchain4j.model.google.genai.GoogleGenAiLiveSessionImpl.realtimeText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.LiveConnectConfig;
import com.google.genai.types.LiveSendClientContentParameters;
import com.google.genai.types.LiveSendRealtimeInputParameters;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleGenAiLiveModelTest {

    private static GoogleGenAiLiveModel.Builder modelBuilder() {
        return GoogleGenAiLiveModel.builder().apiKey("test-key").modelName("gemini-2.0-flash-live-001");
    }

    @Test
    void build_requires_model_name() {
        assertThatThrownBy(
                        () -> GoogleGenAiLiveModel.builder().apiKey("test-key").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
    }

    @Test
    void rejects_more_than_one_response_modality() {
        assertThatThrownBy(
                        () -> modelBuilder().responseModalities("TEXT", "AUDIO").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one response modality");
    }

    @Test
    void defaults_to_a_single_response_modality() {
        LiveConnectConfig config = modelBuilder().build().buildConfig();

        assertThat(config.responseModalities()).isPresent();
        assertThat(config.responseModalities().get()).hasSize(1);
    }

    @Test
    void builds_config_from_builder_options() {
        LiveConnectConfig config = modelBuilder()
                .responseModalities("AUDIO")
                .systemInstruction("be brief")
                .temperature(0.7)
                .maxOutputTokens(256)
                .voiceName("Puck")
                .outputAudioTranscription(true)
                .thinkingBudget(0)
                .build()
                .buildConfig();

        assertThat(config.systemInstruction()).isPresent();
        assertThat(config.temperature()).contains(0.7f);
        assertThat(config.maxOutputTokens()).contains(256);
        assertThat(config.speechConfig()).isPresent();
        assertThat(config.outputAudioTranscription()).isPresent();
        assertThat(config.inputAudioTranscription()).isEmpty();
        assertThat(config.thinkingConfig()).isPresent();
    }

    @Test
    void transcription_is_off_unless_enabled() {
        LiveConnectConfig config = modelBuilder().build().buildConfig();

        assertThat(config.inputAudioTranscription()).isEmpty();
        assertThat(config.outputAudioTranscription()).isEmpty();
    }

    @Test
    void passthrough_config_reaches_options_not_surfaced_as_builder_methods() {
        LiveConnectConfig passthrough = LiveConnectConfig.builder()
                .mediaResolution("MEDIA_RESOLUTION_LOW")
                .build();

        LiveConnectConfig config =
                modelBuilder().liveConnectConfig(passthrough).build().buildConfig();

        assertThat(config.mediaResolution()).isPresent();
    }

    @Test
    void first_class_options_take_precedence_over_passthrough_config() {
        LiveConnectConfig passthrough =
                LiveConnectConfig.builder().temperature(0.1f).build();

        LiveConnectConfig config = modelBuilder()
                .liveConnectConfig(passthrough)
                .temperature(0.9)
                .build()
                .buildConfig();

        assertThat(config.temperature()).contains(0.9f);
    }

    @Test
    void realtime_text_carries_the_text() {
        LiveSendRealtimeInputParameters params = realtimeText("hello");

        assertThat(params.text()).contains("hello");
    }

    @Test
    void realtime_audio_carries_bytes_and_mime_type() {
        byte[] pcm = {1, 2, 3};
        LiveSendRealtimeInputParameters params = realtimeAudio(pcm, "audio/pcm;rate=16000");

        assertThat(params.audio()).isPresent();
        assertThat(params.audio().get().data()).isPresent();
        assertThat(params.audio().get().data().get()).isEqualTo(pcm);
        assertThat(params.audio().get().mimeType()).contains("audio/pcm;rate=16000");
    }

    @Test
    void realtime_audio_stream_end_sets_the_flag() {
        assertThat(realtimeAudioStreamEnd().audioStreamEnd()).contains(true);
    }

    @Test
    void client_content_maps_messages_and_turn_complete() {
        LiveSendClientContentParameters params = clientContent(List.of(UserMessage.from("hi")), true);

        assertThat(params.turnComplete()).contains(true);
        assertThat(params.turns()).isPresent();
        assertThat(params.turns().get()).hasSize(1);
    }
}
