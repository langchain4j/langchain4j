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
            GeminiFinishReason.IMAGE_RECITATION,
            GeminiFinishReason.IMAGE_SAFETY,
            GeminiFinishReason.IMAGE_PROHIBITED_CONTENT,
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
            GeminiFinishReason.OTHER,
            GeminiFinishReason.IMAGE_OTHER,
            GeminiFinishReason.NO_IMAGE,
            GeminiFinishReason.UNEXPECTED_TOOL_CALL,
            GeminiFinishReason.TOO_MANY_TOOL_CALLS,
            GeminiFinishReason.MISSING_THOUGHT_SIGNATURE,
            GeminiFinishReason.MALFORMED_RESPONSE,
            GeminiFinishReason.ESCALATION
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

    @Test
    void should_deserialize_image_safety_finish_reason() {
        GeminiGenerateContentResponse response = Json.fromJson(
                "{\"candidates\":[{\"finishReason\":\"IMAGE_SAFETY\"}]}", GeminiGenerateContentResponse.class);

        assertThat(response.candidates().get(0).finishReason()).hasToString("IMAGE_SAFETY");
    }

    @Test
    void should_deserialize_every_gemini_finish_reason() {
        for (GeminiFinishReason reason : GeminiFinishReason.values()) {
            GeminiGenerateContentResponse response = Json.fromJson(
                    "{\"candidates\":[{\"finishReason\":\"" + reason.name() + "\"}]}",
                    GeminiGenerateContentResponse.class);

            assertThat(response.candidates().get(0).finishReason())
                    .as("deserialization of %s", reason)
                    .isEqualTo(reason);
        }
    }
}
