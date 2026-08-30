package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

class BedrockFinishReasonTest {

    private final BedrockChatModel model = BedrockChatModel.builder()
            .client(mock(BedrockRuntimeClient.class))
            .modelId("test-model")
            .build();

    @Test
    void should_map_model_context_window_exceeded_to_length() {
        assertThat(model.finishReasonFrom(StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED))
                .isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_map_malformed_model_output_to_other() {
        assertThat(model.finishReasonFrom(StopReason.MALFORMED_MODEL_OUTPUT)).isEqualTo(FinishReason.OTHER);
    }

    @Test
    void should_map_malformed_tool_use_to_other() {
        assertThat(model.finishReasonFrom(StopReason.MALFORMED_TOOL_USE)).isEqualTo(FinishReason.OTHER);
    }

    @Test
    void should_map_unknown_to_sdk_version_to_other() {
        assertThat(model.finishReasonFrom(StopReason.UNKNOWN_TO_SDK_VERSION)).isEqualTo(FinishReason.OTHER);
    }

    @Test
    void should_map_every_stop_reason_to_a_finish_reason() {
        for (StopReason stopReason : StopReason.values()) {
            assertThat(model.finishReasonFrom(stopReason))
                    .as("stop reason %s", stopReason)
                    .isNotNull();
        }
    }

    @Test
    void should_map_null_stop_reason_to_null() {
        assertThat(model.finishReasonFrom(null)).isNull();
    }

    @Test
    void should_preserve_existing_stop_reason_mappings() {
        assertThat(model.finishReasonFrom(StopReason.END_TURN)).isEqualTo(FinishReason.STOP);
        assertThat(model.finishReasonFrom(StopReason.STOP_SEQUENCE)).isEqualTo(FinishReason.STOP);
        assertThat(model.finishReasonFrom(StopReason.MAX_TOKENS)).isEqualTo(FinishReason.LENGTH);
        assertThat(model.finishReasonFrom(StopReason.TOOL_USE)).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(model.finishReasonFrom(StopReason.CONTENT_FILTERED)).isEqualTo(FinishReason.CONTENT_FILTER);
        assertThat(model.finishReasonFrom(StopReason.GUARDRAIL_INTERVENED)).isEqualTo(FinishReason.CONTENT_FILTER);
    }
}
