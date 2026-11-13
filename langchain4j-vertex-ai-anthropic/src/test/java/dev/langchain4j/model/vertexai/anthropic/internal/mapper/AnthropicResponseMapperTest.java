package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import org.junit.jupiter.api.Test;

class AnthropicResponseMapperTest {

    @Test
    void should_map_stop_reasons_to_finish_reasons() {
        assertThat(finishReason("end_turn")).isEqualTo(FinishReason.STOP);
        assertThat(finishReason("stop_sequence")).isEqualTo(FinishReason.STOP);
        assertThat(finishReason("max_tokens")).isEqualTo(FinishReason.LENGTH);
        assertThat(finishReason("model_context_window_exceeded")).isEqualTo(FinishReason.LENGTH);
        assertThat(finishReason("tool_use")).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(finishReason("refusal")).isEqualTo(FinishReason.CONTENT_FILTER);
        assertThat(finishReason("pause_turn")).isEqualTo(FinishReason.OTHER);
        assertThat(finishReason(null)).isEqualTo(FinishReason.OTHER);
    }

    private static FinishReason finishReason(String stopReason) {
        AnthropicResponse response = new AnthropicResponse();
        response.stopReason = stopReason;
        return AnthropicResponseMapper.toChatResponse(response).finishReason();
    }
}
