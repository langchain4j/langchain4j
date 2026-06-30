package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.vertexai.api.Candidate;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class FinishReasonMapperTest {

    @ParameterizedTest
    @EnumSource(
            value = Candidate.FinishReason.class,
            names = {"RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII"})
    void should_map_content_policy_reasons_to_content_filter(Candidate.FinishReason finishReason) {
        assertThat(FinishReasonMapper.map(finishReason)).isEqualTo(FinishReason.CONTENT_FILTER);
    }

    @ParameterizedTest
    @CsvSource({
        "STOP, STOP",
        "MAX_TOKENS, LENGTH",
        "SAFETY, CONTENT_FILTER",
        "MALFORMED_FUNCTION_CALL, OTHER",
        "FINISH_REASON_UNSPECIFIED, OTHER"
    })
    void should_map_known_and_unmapped_reasons(Candidate.FinishReason finishReason, FinishReason expected) {
        assertThat(FinishReasonMapper.map(finishReason)).isEqualTo(expected);
    }
}
