package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

class FinishReasonMapperTest {

    @Test
    void should_map_stop_to_stop() {
        assertThat(FinishReasonMapper.fromGFinishReasonToFinishReason(GeminiFinishReason.STOP))
                .isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_map_max_tokens_to_length() {
        assertThat(FinishReasonMapper.fromGFinishReasonToFinishReason(GeminiFinishReason.MAX_TOKENS))
                .isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_map_safety_related_reasons_to_content_filter() {
        GeminiFinishReason[] safetyReasons = {
            GeminiFinishReason.BLOCKLIST,
            GeminiFinishReason.PROHIBITED_CONTENT,
            GeminiFinishReason.RECITATION,
            GeminiFinishReason.SPII,
            GeminiFinishReason.SAFETY,
            GeminiFinishReason.LANGUAGE
        };
        for (GeminiFinishReason reason : safetyReasons) {
            assertThat(FinishReasonMapper.fromGFinishReasonToFinishReason(reason))
                    .as("mapping for %s", reason)
                    .isEqualTo(FinishReason.CONTENT_FILTER);
        }
    }

    @Test
    void should_map_unspecified_malformed_and_other_to_other() {
        GeminiFinishReason[] otherReasons = {
            GeminiFinishReason.MALFORMED_FUNCTION_CALL,
            GeminiFinishReason.FINISH_REASON_UNSPECIFIED,
            GeminiFinishReason.OTHER
        };
        for (GeminiFinishReason reason : otherReasons) {
            assertThat(FinishReasonMapper.fromGFinishReasonToFinishReason(reason))
                    .as("mapping for %s", reason)
                    .isEqualTo(FinishReason.OTHER);
        }
    }

    @Test
    void should_map_every_gemini_finish_reason_to_a_non_null_finish_reason() {
        for (GeminiFinishReason reason : GeminiFinishReason.values()) {
            assertThat(FinishReasonMapper.fromGFinishReasonToFinishReason(reason))
                    .as("mapping for %s", reason)
                    .isNotNull();
        }
    }
}
