package dev.langchain4j.model.mistralai.internal.mapper;

import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.finishReasonFrom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MistralAiMapperFinishReasonTest {

    @ParameterizedTest
    @CsvSource({
        "stop, STOP",
        "length, LENGTH",
        "model_length, LENGTH",
        "tool_calls, TOOL_EXECUTION",
        "content_filter, CONTENT_FILTER",
        "error, OTHER",
        "something_new, OTHER"
    })
    void should_map_finish_reason(String mistralAiFinishReason, FinishReason expected) {
        assertThat(finishReasonFrom(mistralAiFinishReason)).isEqualTo(expected);
    }

    @Test
    void should_map_null_finish_reason_to_null() {
        assertThat(finishReasonFrom(null)).isNull();
    }
}
