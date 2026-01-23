package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiGenerateContentResponse(
        String responseId, String modelVersion, List<GeminiCandidate> candidates, GeminiUsageMetadata usageMetadata) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCandidate(GeminiContent content, GeminiFinishReason finishReason) {
        enum GeminiFinishReason {
            FINISH_REASON_UNSPECIFIED,
            STOP,
            MAX_TOKENS,
            SAFETY,
            RECITATION,
            LANGUAGE,
            OTHER,
            BLOCKLIST,
            PROHIBITED_CONTENT,
            SPII,
            MALFORMED_FUNCTION_CALL
        }
    }
}
